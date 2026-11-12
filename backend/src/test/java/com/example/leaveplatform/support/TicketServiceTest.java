package com.example.leaveplatform.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.organization.Department;

/**
 * Who may say what about a ticket, and what the ticket looks like afterwards.
 *
 * <p>The cases worth testing are the ones that a friendly screen would never
 * show: a colleague who types another person's ticket number into the address
 * bar, a staff account that asks to be treated as the desk, and a closed ticket
 * somebody tries to keep writing in. The buttons are not the rule - the service
 * is.
 */
class TicketServiceTest {

    private static final String STAFF = "sara.ahmed";
    private static final String DESK = "admin";
    private static final String OTHER = "someone.else";
    private static final long STAFF_ID = 11L;
    private static final long DESK_ID = 22L;
    private static final long OTHER_ID = 33L;

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final TicketMessageRepository lines = mock(TicketMessageRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final TicketNotifier notifier = mock(TicketNotifier.class);

    private final Department department = mock(Department.class);
    private final Employee staff = mock(Employee.class);
    private final Employee deskMember = mock(Employee.class);
    private final Employee otherPerson = mock(Employee.class);

    private final TicketService service =
            new TicketService(tickets, lines, users, audit, notifier);

    @BeforeEach
    void commonStubs() {
        when(department.getId()).thenReturn(1L);
        when(department.getName()).thenReturn("Centre for Quality");
        describe(staff, STAFF_ID, "Sara Ahmed", "ADMIN-001");
        describe(deskMember, DESK_ID, "Nadia Ahmed", "ADMIN-002");
        describe(otherPerson, OTHER_ID, "Another Person", "CS-003");

        AppUser staffAccount = account(STAFF, staff, false);
        AppUser deskAccount = account(DESK, deskMember, true);
        AppUser otherAccount = account(OTHER, otherPerson, false);
        when(users.findByUsername(STAFF)).thenReturn(Optional.of(staffAccount));
        when(users.findByUsername(DESK)).thenReturn(Optional.of(deskAccount));
        when(users.findByUsername(OTHER)).thenReturn(Optional.of(otherAccount));
        when(users.findEnabledWithRole(AppRole.CENTRAL_ADMIN)).thenReturn(List.of(deskAccount));
        when(lines.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void describe(Employee person, long id, String name, String number) {
        when(person.getId()).thenReturn(id);
        when(person.getFullName()).thenReturn(name);
        when(person.getEmployeeNo()).thenReturn(number);
        when(person.getDepartment()).thenReturn(department);
        when(person.isActive()).thenReturn(true);
    }

    private AppUser account(String username, Employee person, boolean desk) {
        AppUser user = mock(AppUser.class);
        AppRole role = mock(AppRole.class);
        when(role.getCode()).thenReturn(desk ? AppRole.CENTRAL_ADMIN : "EMPLOYEE");
        when(user.getUsername()).thenReturn(username);
        when(user.getEmployee()).thenReturn(person);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(user.isEnabled()).thenReturn(true);
        return user;
    }

    /** The row the database would hand back: an id, a reference and the chosen state. */
    private SupportTicket stored(TicketStatus status, Employee assignee) {
        SupportTicket ticket = new SupportTicket(staff, department, TicketCategory.LEAVE_RECORD,
                "My casual leave balance is short by two days");
        setId(ticket, 7L);
        ticket.stampReference();
        Employee holder = assignee == null ? deskMember : assignee;
        switch (status) {
            case OPEN -> {
            }
            case IN_PROGRESS -> ticket.assign(holder);
            case ANSWERED -> {
                ticket.assign(holder);
                ticket.answeredByDesk();
            }
            case CLOSED -> {
                ticket.assign(holder);
                ticket.close();
            }
        }
        return ticket;
    }

    private void setId(SupportTicket ticket, long id) {
        try {
            var field = SupportTicket.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(ticket, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void aNewTicketIsNumberedFromItsOwnIdAndGoesToStrTheDesk() {
        when(tickets.save(any())).thenAnswer(call -> {
            SupportTicket saved = call.getArgument(0);
            setId(saved, 42L);
            return saved;
        });

        TicketResponse result = service.submit(STAFF,
                new SubmitTicketRequest("LEAVE_RECORD", "My balance is short", "Two days missing."));

        assertThat(result.reference()).isEqualTo("HD-000042");
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(result.openedByName()).isEqualTo("Sara Ahmed");
        ArgumentCaptor<TicketMessage> written = ArgumentCaptor.forClass(TicketMessage.class);
        verify(lines).save(written.capture());
        assertThat(written.getValue().getBody()).isEqualTo("Two days missing.");
        assertThat(written.getValue().isFromDesk()).isFalse();
        verify(notifier).opened(any(), any());
    }

    @Test
    void aTicketWithoutAnyDetailStillOpensWithItsSubject() {
        when(tickets.save(any())).thenAnswer(call -> {
            SupportTicket saved = call.getArgument(0);
            setId(saved, 43L);
            return saved;
        });

        TicketResponse result = service.submit(STAFF,
                new SubmitTicketRequest("OTHER", "A question", "   "));

        assertThat(result.reference()).isEqualTo("HD-000043");
        verify(lines, never()).save(any());
    }

    @Test
    void aTicketNeedsALineAboutItselfAndAChosenKind() {
        assertThatThrownBy(() -> service.submit(STAFF, new SubmitTicketRequest("OTHER", "  ", null)))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("Write one short line saying what you need.");
        assertThatThrownBy(() -> service.submit(STAFF, new SubmitTicketRequest(null, "A subject", null)))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("Choose what the ticket is about.");
        assertThatThrownBy(() -> service.submit(STAFF, new SubmitTicketRequest("NOT_A_KIND", "A subject", null)))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("Choose one of the kinds of help on the screen.");
    }

    @Test
    void anAccountWithNoActiveStaffRecordCannotOpenATicket() {
        AppUser lonely = account("nobody", null, false);
        when(users.findByUsername("nobody")).thenReturn(Optional.of(lonely));

        assertThatThrownBy(() -> service.submit("nobody", new SubmitTicketRequest("OTHER", "A", null)))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("Your account has no active staff record, so the desk cannot see who is asking.");
    }

    @Test
    void theDeskAnswerMarksTheTicketAnsweredAndTellsThePersonWhoAsked() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        TicketResponse result = service.replyByDesk(DESK, 7L, "Corrected, check your balance now.");

        assertThat(result.status()).isEqualTo(TicketStatus.ANSWERED);
        assertThat(result.statusLabel()).isEqualTo("Answered, waiting for you");
        ArgumentCaptor<TicketMessage> written = ArgumentCaptor.forClass(TicketMessage.class);
        verify(lines).save(written.capture());
        assertThat(written.getValue().isFromDesk()).isTrue();
        verify(audit).recordAs(eq(DESK), eq(AuditAction.TICKET_ANSWERED), eq("SUPPORT_TICKET"), eq(7L),
                any());
        verify(notifier).repliedByDesk(eq(ticket), any(), eq("Corrected, check your balance now."));
    }

    @Test
    void aStaffReplyAfterAnAnswerPutsTheTicketBackInFrontOfTheDesk() {
        SupportTicket ticket = stored(TicketStatus.ANSWERED, deskMember);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        TicketResponse result = service.replyByStaff(STAFF, 7L, "It is still short by one day.");

        assertThat(result.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());
        verify(notifier).repliedByStaff(eq(ticket), any(), any());
    }

    @Test
    void anUnassignedTicketRepliesIntoTheOpenQueueRatherThanInventingAnOwner() {
        SupportTicket ticket = stored(TicketStatus.ANSWERED, deskMember);
        ticket.assign(null);
        ticket.answeredByStaff();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void aColleagueWhoTypesTheNumberIsRefusedRatherThanShownTheThread() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.thread(OTHER, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        TicketResponse own = service.thread(STAFF, 7L).ticket();
        assertThat(own.reference()).isEqualTo("HD-000007");
        TicketResponse asDesk = service.thread(DESK, 7L).ticket();
        assertThat(asDesk.reference()).isEqualTo("HD-000007");
    }

    @Test
    void writingOnSomebodyElsesTicketIsRefusedEvenThoughTheNumberIsGuessable() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.replyByStaff(OTHER, 7L, "I will answer this myself."))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.replyByStaff(STAFF, 99L, "Any text"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void aStaffAccountCannotSpeakAsTheDeskOrAssignOrClose() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.replyByDesk(STAFF, 7L, "I am the desk today."))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("The help desk is run by the administration office.");
        assertThatThrownBy(() -> service.assign(STAFF, 7L, DESK))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.close(STAFF, 7L, "Done."))
                .isInstanceOf(ResponseStatusException.class);
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());
    }

    @Test
    void assigningPicksAnAccountThatCanActuallyAnswer() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        TicketResponse result = service.assign(DESK, 7L, DESK);

        assertThat(result.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(result.assigneeName()).isEqualTo("Nadia Ahmed");
        verify(notifier).assigned(eq(ticket), any());

        assertThatThrownBy(() -> service.assign(DESK, 7L, STAFF))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("That account is not a sign-in account of the help desk.");
        assertThatThrownBy(() -> service.assign(DESK, 7L, "  "))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("Choose who at the desk should handle this ticket.");
    }

    @Test
    void closingRecordsTheLastLineAndRefusesToCloseTwice() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        TicketResponse result = service.close(DESK, 7L, "Balance corrected on 6 Nov.");

        assertThat(result.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.closedAt()).isNotNull();
        verify(audit).recordAs(eq(DESK), eq(AuditAction.TICKET_CLOSED), eq("SUPPORT_TICKET"), eq(7L), any());
        verify(notifier).closed(eq(ticket), any(), eq("Balance corrected on 6 Nov."));

        assertThatThrownBy(() -> service.close(DESK, 7L, null))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("This ticket is already closed.");
    }

    @Test
    void aTicketClosedWithoutALastLineStillCloses() {
        SupportTicket ticket = stored(TicketStatus.IN_PROGRESS, deskMember);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        assertThat(service.close(DESK, 7L, "   ").status()).isEqualTo(TicketStatus.CLOSED);
        verify(lines, never()).save(any());
    }

    @Test
    void thePersonWhoAskedMayReopenButTheDeskMayNot() {
        SupportTicket ticket = stored(TicketStatus.CLOSED, deskMember);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        TicketResponse result = service.reopen(STAFF, 7L, "It is wrong again.");

        assertThat(result.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(result.closedAt()).isNull();
        verify(notifier).reopened(eq(ticket), any(), eq("It is wrong again."));

        assertThatThrownBy(() -> service.reopen(DESK, 7L, "Let me open it again."))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void anOpenTicketIsNotReopenedAndAClosedOneIsNotRepliedTo() {
        SupportTicket open = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service.reopen(STAFF, 7L, "Again!"))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("This ticket is not closed.");

        SupportTicket closed = stored(TicketStatus.CLOSED, null);
        when(tickets.findByIdForUpdate(8L)).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> service.replyByStaff(STAFF, 8L, "One more line."))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("This ticket is closed. Write in it again to reopen it.");
        assertThatThrownBy(() -> service.replyByDesk(DESK, 8L, "An afterthought."))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("This ticket is closed, so the desk cannot write in it.");
        assertThatThrownBy(() -> service.assign(DESK, 8L, DESK))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("This ticket is closed, so there is nothing to hand over.");
    }

    @Test
    void aReplyThatIsTooLongIsRefusedRatherThanCutQuietly() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findByIdForUpdate(7L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.replyByStaff(STAFF, 7L, "x".repeat(TicketMessage.MAX_BODY + 1)))
                .isInstanceOf(TicketRuleException.class)
                .hasMessage("Keep each line to 2000 characters.");
    }

    @Test
    void theQueueAsksTheDatabaseForTheWaitingTicketsAndCountsThem() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findQueue(any(), any(Pageable.class))).thenReturn(List.of(ticket));
        when(tickets.countByStatusIn(any())).thenReturn(3L);

        assertThat(service.queue()).singleElement()
                .satisfies(found -> assertThat(found.reference()).isEqualTo("HD-000007"));
        assertThat(service.waitingForDesk()).isEqualTo(3L);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(tickets).findQueue(eq(List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)),
                page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(TicketService.LIST_LIMIT);
    }

    @Test
    void aStaffListIsAlwaysTakenFromTheSessionAndNeverFromTheCaller() {
        SupportTicket ticket = stored(TicketStatus.OPEN, null);
        when(tickets.findLatestByEmployee(eq(STAFF_ID), any(Pageable.class))).thenReturn(List.of(ticket));

        assertThat(service.mine(STAFF)).singleElement()
                .satisfies(found -> assertThat(found.employeeNo()).isEqualTo("ADMIN-001"));
        verify(tickets).findLatestByEmployee(eq(STAFF_ID), any(Pageable.class));
        verify(tickets, never()).findLatestByEmployee(eq(OTHER_ID), any(Pageable.class));
    }

    @Test
    void anAccountWithNoStaffRecordHasAnEmptyListRatherThanAnError() {
        AppUser lonely = account("nobody", null, false);
        when(users.findByUsername("nobody")).thenReturn(Optional.of(lonely));

        assertThat(service.mine("nobody")).isEmpty();
        verify(tickets, never()).findLatestByEmployee(anyLong(), any(Pageable.class));
    }

    @Test
    void theAssignmentBoxOffersOnlyDeskAccountsAndNamesThem() {
        assertThat(service.deskMembers()).singleElement()
                .satisfies(member -> {
                    assertThat(member.username()).isEqualTo(DESK);
                    assertThat(member.displayName()).isEqualTo("Nadia Ahmed (admin)");
                });
    }
}
