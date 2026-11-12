package com.example.leaveplatform.support;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Says who should hear about a ticket and in what words.
 *
 * <p>{@link TicketService} keeps the rules and calls this in the same
 * transaction it writes in, so a ticket that did not happen leaves no message
 * behind. The desk has no queue of its own: the people told are the enabled
 * central administrator accounts, which is the same group the screens hand the
 * ticket to. Nobody is told about a line they wrote themselves.
 *
 * <p>The message carries the ticket id in the column the bell already uses for
 * "the row this message is about". That is only safe because every ticket kind
 * opens the help desk screen, where the number means a ticket; a message that
 * clicked through to a leave screen would be pointing at somebody else's dates.
 */
@Component
class TicketNotifier {

    private final AppUserRepository users;
    private final NotificationService notifications;

    TicketNotifier(AppUserRepository users, NotificationService notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    /** A new question arrives in the desk's queue. */
    void opened(SupportTicket ticket, AppUser actor) {
        sendAll(desk(), actor, NotificationKind.TICKET_WAITING,
                "A help-desk ticket is waiting",
                quoted(ticket) + " Asked by " + ticket.getOpenedBy().getFullName() + ".",
                ticket.getId());
    }

    /** The desk gave it to one of its own. */
    void assigned(SupportTicket ticket, AppUser actor) {
        send(usernameOf(ticket.getAssignee()), actor, NotificationKind.TICKET_WAITING,
                "A help-desk ticket was assigned to you",
                quoted(ticket) + " Assigned by " + actor.getEmployee().getFullName()
                        + ", it is waiting for your answer.",
                ticket.getId());
    }

    /** The person who asked has written again, so an answer is due. */
    void repliedByStaff(SupportTicket ticket, AppUser actor, String body) {
        if (ticket.getAssignee() != null) {
            send(usernameOf(ticket.getAssignee()), actor, NotificationKind.TICKET_WAITING,
                    "A staff member replied to a ticket you are handling",
                    quoted(ticket) + " " + line(actor, body), ticket.getId());
            return;
        }
        sendAll(desk(), actor, NotificationKind.TICKET_WAITING,
                "A staff member replied to a help-desk ticket",
                quoted(ticket) + " " + line(actor, body), ticket.getId());
    }

    /** The desk answered, so the next word is expected from the person who asked. */
    void repliedByDesk(SupportTicket ticket, AppUser actor, String body) {
        send(usernameOf(ticket.getOpenedBy()), actor, NotificationKind.TICKET_ANSWERED,
                "The help desk answered your ticket",
                quoted(ticket) + " " + line(actor, body), ticket.getId());
    }

    /** The matter is finished; the person who asked is told either way. */
    void closed(SupportTicket ticket, AppUser actor, String body) {
        send(usernameOf(ticket.getOpenedBy()), actor, NotificationKind.TICKET_CLOSED,
                "Your help-desk ticket was closed",
                quoted(ticket) + " Closed by " + actor.getEmployee().getFullName() + "."
                        + (body == null ? " Write again from the same ticket if it comes back."
                                : " " + excerpt(body)),
                ticket.getId());
    }

    /** A closed ticket came back, so an answer is due again. */
    void reopened(SupportTicket ticket, AppUser actor, String body) {
        if (ticket.getAssignee() != null) {
            send(usernameOf(ticket.getAssignee()), actor, NotificationKind.TICKET_WAITING,
                    "A closed help-desk ticket was reopened",
                    quoted(ticket) + " " + line(actor, body), ticket.getId());
            return;
        }
        sendAll(desk(), actor, NotificationKind.TICKET_WAITING,
                "A closed help-desk ticket was reopened",
                quoted(ticket) + " " + line(actor, body), ticket.getId());
    }

    private static String quoted(SupportTicket ticket) {
        return ticket.getReference() + ": " + ticket.getSubject() + ".";
    }

    /** Who wrote it and what they said, in the words the bell shows. */
    private static String line(AppUser actor, String body) {
        return actor.getEmployee().getFullName() + " wrote: " + excerpt(body);
    }

    private static String excerpt(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    private List<String> desk() {
        return users.findEnabledWithRole(AppRole.CENTRAL_ADMIN).stream()
                .map(AppUser::getUsername)
                .toList();
    }

    private Optional<String> usernameOf(Employee person) {
        if (person == null) {
            return Optional.empty();
        }
        return users.findByEmployeeId(person.getId())
                .filter(AppUser::isEnabled)
                .map(AppUser::getUsername);
    }

    private void send(Optional<String> recipient, AppUser actor, NotificationKind kind, String title,
            String body, Long ticketId) {
        recipient.ifPresent(username -> send(username, actor, kind, title, body, ticketId));
    }

    private void sendAll(List<String> recipients, AppUser actor, NotificationKind kind, String title,
            String body, Long ticketId) {
        recipients.forEach(username -> send(username, actor, kind, title, body, ticketId));
    }

    private void send(String recipient, AppUser actor, NotificationKind kind, String title, String body,
            Long ticketId) {
        if (recipient == null || (actor != null && recipient.equals(actor.getUsername()))) {
            return;
        }
        notifications.notifyUser(recipient, kind, title, body, ticketId);
    }
}
