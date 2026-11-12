package com.example.leaveplatform.support;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;

/**
 * The help desk: what a member of staff may ask for and what the desk may do
 * about it.
 *
 * <p>Two rules run through the whole class. The first is that <b>which side is
 * speaking is taken from the account, never from the request</b>: {@code
 * from_desk} on a line, and whether a caller may open a queue at all, both come
 * from the roles on the session. The second is that the person who opened a
 * ticket and the desk are the only readers of it - a ticket often quotes a
 * personal record, so a colleague who guesses the number gets 403 rather than
 * the conversation.
 *
 * <p>A staff member's own actions are not audited: opening a ticket and writing
 * in it are ordinary business, and the thread already keeps every word with its
 * author and time. The desk's actions are audited, because handing a question to
 * a named person, answering as the university and closing a matter are decisions
 * about somebody else.
 */
@Service
public class TicketService {

    /** A list shows the newest (or, in the queue, the longest waiting) {@value} rows. */
    public static final int LIST_LIMIT = 200;

    private static final List<TicketStatus> WAITING = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

    private final SupportTicketRepository tickets;
    private final TicketMessageRepository lines;
    private final AppUserRepository users;
    private final AuditService audit;
    private final TicketNotifier notifier;

    public TicketService(SupportTicketRepository tickets, TicketMessageRepository lines,
            AppUserRepository users, AuditService audit, TicketNotifier notifier) {
        this.tickets = tickets;
        this.lines = lines;
        this.users = users;
        this.audit = audit;
        this.notifier = notifier;
    }

    @Transactional
    public TicketResponse submit(String username, SubmitTicketRequest request) {
        AppUser user = account(username);
        Employee person = staffRecord(user);
        String subject = required(request.subject(), SupportTicket.MAX_SUBJECT,
                "Write one short line saying what you need.");
        TicketCategory category = category(request.category());

        SupportTicket saved = tickets.save(
                new SupportTicket(person, person.getDepartment(), category, subject));
        saved.stampReference();
        String detail = trimmed(request.detail());
        if (detail != null) {
            lines.save(new TicketMessage(saved, person, false, limited(detail)));
        }
        notifier.opened(saved, user);
        return TicketResponse.of(saved);
    }

    @Transactional
    public TicketResponse replyByStaff(String username, Long ticketId, String body) {
        AppUser user = account(username);
        SupportTicket ticket = read(ticketId);
        ownTicket(user, ticket);
        if (!ticket.getStatus().isOpen()) {
            throw new TicketRuleException("This ticket is closed. Write in it again to reopen it.");
        }
        String text = required(body, TicketMessage.MAX_BODY, "Type the line you want to add.");
        lines.save(new TicketMessage(ticket, user.getEmployee(), false, text));
        ticket.answeredByStaff();
        notifier.repliedByStaff(ticket, user, text);
        return TicketResponse.of(ticket);
    }

    @Transactional
    public TicketResponse replyByDesk(String username, Long ticketId, String body) {
        AppUser user = deskAccount(username);
        SupportTicket ticket = read(ticketId);
        if (!ticket.getStatus().isOpen()) {
            throw new TicketRuleException("This ticket is closed, so the desk cannot write in it.");
        }
        String text = required(body, TicketMessage.MAX_BODY, "Type the answer you want to send.");
        lines.save(new TicketMessage(ticket, user.getEmployee(), true, text));
        ticket.answeredByDesk();
        audit.recordAs(username, AuditAction.TICKET_ANSWERED, "SUPPORT_TICKET", ticket.getId(),
                "Answered " + ticket.getReference() + " (" + ticket.getSubject() + ").");
        notifier.repliedByDesk(ticket, user, text);
        return TicketResponse.of(ticket);
    }

    /**
     * Hands the ticket to one named person at the desk. The assignee is taken
     * from the account list the screen offers, so it is always somebody who can
     * sign in and answer; a typed name would leave a ticket "assigned" to nobody.
     */
    @Transactional
    public TicketResponse assign(String username, Long ticketId, String assigneeUsername) {
        AppUser user = deskAccount(username);
        SupportTicket ticket = read(ticketId);
        if (!ticket.getStatus().isOpen()) {
            throw new TicketRuleException("This ticket is closed, so there is nothing to hand over.");
        }
        String name = trimmed(assigneeUsername);
        if (name == null) {
            throw new TicketRuleException("Choose who at the desk should handle this ticket.");
        }
        AppUser assignee = users.findByUsername(name)
                .filter(AppUser::isEnabled)
                .filter(found -> hasRole(found, AppRole.CENTRAL_ADMIN))
                .orElseThrow(() -> new TicketRuleException(
                        "That account is not a sign-in account of the help desk."));
        ticket.assign(staffRecord(assignee));
        audit.recordAs(username, AuditAction.TICKET_ASSIGNED, "SUPPORT_TICKET", ticket.getId(),
                ticket.getReference() + " assigned to " + assignee.getUsername() + ".");
        notifier.assigned(ticket, user);
        return TicketResponse.of(ticket);
    }

    /**
     * Ends the matter. The last line is optional because plenty of tickets are
     * finished with a phone call or a visit, and the desk still has to be able to
     * say so here.
     */
    @Transactional
    public TicketResponse close(String username, Long ticketId, String finalBody) {
        AppUser user = deskAccount(username);
        SupportTicket ticket = read(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new TicketRuleException("This ticket is already closed.");
        }
        String text = trimmed(finalBody);
        if (text != null) {
            lines.save(new TicketMessage(ticket, user.getEmployee(), true, limited(text)));
        }
        ticket.close();
        audit.recordAs(username, AuditAction.TICKET_CLOSED, "SUPPORT_TICKET", ticket.getId(),
                "Closed " + ticket.getReference() + " (" + ticket.getSubject() + ")"
                        + (text == null ? "." : "; last line: " + excerpt(text)));
        notifier.closed(ticket, user, text);
        return TicketResponse.of(ticket);
    }

    /**
     * The person who asked says the problem came back. Only they may do this: the
     * desk closing a ticket is a decision, and letting the desk open it again
     * would put the same decision twice in front of two different screens.
     */
    @Transactional
    public TicketResponse reopen(String username, Long ticketId, String body) {
        AppUser user = account(username);
        SupportTicket ticket = read(ticketId);
        ownTicket(user, ticket);
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            throw new TicketRuleException("This ticket is not closed.");
        }
        String text = required(body, TicketMessage.MAX_BODY, "Say what is still wrong.");
        lines.save(new TicketMessage(ticket, user.getEmployee(), false, text));
        ticket.reopen();
        notifier.reopened(ticket, user, text);
        return TicketResponse.of(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> mine(String username) {
        AppUser user = account(username);
        Employee person = user.getEmployee();
        if (person == null) {
            return List.of();
        }
        return tickets.findLatestByEmployee(person.getId(), PageRequest.of(0, LIST_LIMIT)).stream()
                .map(TicketResponse::of)
                .toList();
    }

    /** The desk's queue: the longest-waiting first, because that is the answer owed. */
    @Transactional(readOnly = true)
    public List<TicketResponse> queue() {
        return tickets.findQueue(WAITING, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(TicketResponse::of)
                .toList();
    }

    /** Every ticket, newest first, for the administrator who wants the whole picture. */
    @Transactional(readOnly = true)
    public List<TicketResponse> all() {
        return tickets.findLatest(PageRequest.of(0, LIST_LIMIT)).stream()
                .map(TicketResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public long waitingForDesk() {
        return tickets.countByStatusIn(WAITING);
    }

    /**
     * The conversation, oldest line first. Readable by the person who opened the
     * ticket and by the desk, and by nobody else.
     */
    @Transactional(readOnly = true)
    public TicketThreadResponse thread(String username, Long ticketId) {
        AppUser user = account(username);
        SupportTicket ticket = read(ticketId);
        boolean opened = user.getEmployee() != null
                && Objects.equals(ticket.getOpenedBy().getId(), user.getEmployee().getId());
        if (!opened && !hasRole(user, AppRole.CENTRAL_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the person who asked and the help desk can read this ticket.");
        }
        List<TicketMessageResponse> messages = lines.findThread(ticket.getId()).stream()
                .map(TicketMessageResponse::of)
                .toList();
        return new TicketThreadResponse(TicketResponse.of(ticket), messages);
    }

    /** The accounts the assignment box may offer, in the order a person reads them. */
    @Transactional(readOnly = true)
    public List<DeskMemberResponse> deskMembers() {
        return users.findEnabledWithRole(AppRole.CENTRAL_ADMIN).stream()
                .map(account -> new DeskMemberResponse(account.getUsername(),
                        account.getEmployee() == null
                                ? account.getUsername()
                                : account.getEmployee().getFullName() + " (" + account.getUsername() + ")"))
                .sorted(Comparator.comparing(DeskMemberResponse::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private SupportTicket read(Long ticketId) {
        return tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "There is no help-desk ticket number " + ticketId + "."));
    }

    private AppUser account(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AppUser deskAccount(String username) {
        AppUser user = account(username);
        if (!hasRole(user, AppRole.CENTRAL_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The help desk is run by the administration office.");
        }
        return user;
    }

    private void ownTicket(AppUser user, SupportTicket ticket) {
        Employee person = user.getEmployee();
        if (person == null || !Objects.equals(ticket.getOpenedBy().getId(), person.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This ticket was not opened by you.");
        }
    }

    private static Employee staffRecord(AppUser user) {
        Employee person = user.getEmployee();
        if (person == null || !person.isActive()) {
            throw new TicketRuleException(
                    "Your account has no active staff record, so the desk cannot see who is asking.");
        }
        return person;
    }

    private static TicketCategory category(String code) {
        String name = trimmed(code);
        if (name == null) {
            throw new TicketRuleException("Choose what the ticket is about.");
        }
        try {
            return TicketCategory.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new TicketRuleException("Choose one of the kinds of help on the screen.");
        }
    }

    private static String required(String text, int maximum, String missing) {
        String value = trimmed(text);
        if (value == null) {
            throw new TicketRuleException(missing);
        }
        return limited(value, maximum);
    }

    private static String limited(String value) {
        return limited(value, TicketMessage.MAX_BODY);
    }

    private static String limited(String value, int maximum) {
        if (value.length() > maximum) {
            throw new TicketRuleException("Keep each line to " + maximum + " characters.");
        }
        return value;
    }

    private static String excerpt(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private static boolean hasRole(AppUser user, String roleCode) {
        return user.getRoles().stream().anyMatch(role -> roleCode.equals(role.getCode()));
    }

    private static String trimmed(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
