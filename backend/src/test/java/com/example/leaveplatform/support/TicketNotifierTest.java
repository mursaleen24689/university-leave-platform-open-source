package com.example.leaveplatform.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.organization.Department;

/**
 * Who hears about a ticket, and in what words. The bell is how a member of staff
 * finds out that the desk has answered, so a missed message is a question left
 * unanswered while everybody believes it was dealt with.
 */
class TicketNotifierTest {

    private static final long TICKET_ID = 7L;
    private static final String QUOTED = "HD-000007: I cannot sign in any more.";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final NotificationService messages = mock(NotificationService.class);
    private final TicketNotifier notifier = new TicketNotifier(users, messages);

    private final Department department = mock(Department.class);
    private final Employee asker = mock(Employee.class);
    private final Employee deskOne = mock(Employee.class);
    private final Employee deskTwo = mock(Employee.class);
    private final AppUser askerAccount = mock(AppUser.class);
    private final AppUser adminOne = mock(AppUser.class);
    private final AppUser adminTwo = mock(AppUser.class);

    @BeforeEach
    void commonStubs() {
        when(department.getName()).thenReturn("Centre for Quality");
        when(asker.getId()).thenReturn(1L);
        when(asker.getFullName()).thenReturn("Sara Ahmed");
        when(deskOne.getId()).thenReturn(2L);
        when(deskOne.getFullName()).thenReturn("Nadia Ahmed");
        when(deskTwo.getId()).thenReturn(3L);
        named(adminOne, "admin", deskOne);
        named(adminTwo, "second.admin", deskTwo);
        named(askerAccount, "sara.ahmed", asker);
        when(users.findEnabledWithRole(AppRole.CENTRAL_ADMIN)).thenReturn(List.of(adminOne, adminTwo));
        when(users.findByEmployeeId(1L)).thenReturn(Optional.of(askerAccount));
        when(users.findByEmployeeId(2L)).thenReturn(Optional.of(adminOne));
    }

    private void named(AppUser account, String username, Employee person) {
        when(account.getUsername()).thenReturn(username);
        when(account.getEmployee()).thenReturn(person);
        when(account.isEnabled()).thenReturn(true);
    }

    private SupportTicket ticket(Employee assignee) {
        SupportTicket ticket = new SupportTicket(asker, department, TicketCategory.ACCOUNT_ACCESS,
                "I cannot sign in any more");
        try {
            var field = SupportTicket.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(ticket, TICKET_ID);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        ticket.stampReference();
        if (assignee != null) {
            ticket.assign(assignee);
        }
        return ticket;
    }

    private String lastBodySentTo(String username) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(messages).notifyUser(eq(username), any(), anyString(), body.capture(), any());
        return body.getValue();
    }

    @Test
    void aNewTicketIsReadByEverybodyAtTheDesk() {
        notifier.opened(ticket(null), askerAccount);

        verify(messages).notifyUser(eq("admin"), eq(NotificationKind.TICKET_WAITING),
                eq("A help-desk ticket is waiting"), anyString(), eq(TICKET_ID));
        verify(messages).notifyUser(eq("second.admin"), eq(NotificationKind.TICKET_WAITING),
                anyString(), anyString(), eq(TICKET_ID));
        assertThat(lastBodySentTo("admin")).isEqualTo(QUOTED + " Asked by Sara Ahmed.");
    }

    @Test
    void anAdministratorWhoOpenedTheirOwnTicketIsNotToldAboutIt() {
        notifier.opened(ticket(null), adminOne);

        verify(messages, never()).notifyUser(eq("admin"), any(), anyString(), anyString(), any());
        verify(messages, times(1)).notifyUser(eq("second.admin"), any(), anyString(), anyString(), any());
    }

    @Test
    void anAssignedReplyGoesToThatPersonAloneRatherThanToTheWholeDesk() {
        notifier.repliedByStaff(ticket(deskOne), askerAccount, "Still waiting since Monday.");

        verify(messages).notifyUser(eq("admin"), eq(NotificationKind.TICKET_WAITING),
                eq("A staff member replied to a ticket you are handling"), anyString(), any());
        verify(messages, never()).notifyUser(eq("second.admin"), any(), anyString(), anyString(), any());
        assertThat(lastBodySentTo("admin")).isEqualTo(QUOTED + " Sara Ahmed wrote: Still waiting since Monday.");
    }

    @Test
    void anUnassignedReplyIsReadByTheWholeDesk() {
        notifier.repliedByStaff(ticket(null), askerAccount, "Still waiting since Monday.");

        verify(messages, times(2)).notifyUser(anyString(), eq(NotificationKind.TICKET_WAITING),
                eq("A staff member replied to a help-desk ticket"), anyString(), any());
    }

    @Test
    void anAnswerFromTheDeskGoesToThePersonWhoAsked() {
        notifier.repliedByDesk(ticket(deskOne), adminOne, "Your access is restored.");

        verify(messages).notifyUser(eq("sara.ahmed"), eq(NotificationKind.TICKET_ANSWERED),
                eq("The help desk answered your ticket"),
                eq(QUOTED + " Nadia Ahmed wrote: Your access is restored."), eq(TICKET_ID));
    }

    @Test
    void closingTheTicketTellsThePersonWhoAskedWhatWasSaid() {
        notifier.closed(ticket(deskOne), adminOne, "The account was locked, now cleared.");

        assertThat(lastBodySentTo("sara.ahmed"))
                .isEqualTo(QUOTED + " Closed by Nadia Ahmed. The account was locked, now cleared.");
    }

    @Test
    void aTicketClosedWithoutALastLineStillSaysHowToComeBack() {
        notifier.closed(ticket(null), adminOne, null);

        assertThat(lastBodySentTo("sara.ahmed"))
                .contains("Closed by Nadia Ahmed.")
                .contains("Write again from the same ticket if it comes back.");
    }

    @Test
    void aReopenedTicketIsPutBackInFrontOfTheDesk() {
        notifier.reopened(ticket(null), askerAccount, "It is wrong again.");

        verify(messages, times(2)).notifyUser(anyString(), eq(NotificationKind.TICKET_WAITING),
                eq("A closed help-desk ticket was reopened"), anyString(), any());
    }

    @Test
    void aLongReplyIsQuotedOnlyAsFarAsTheBellCanShowIt() {
        String longBody = "a".repeat(300);

        notifier.repliedByDesk(ticket(null), adminOne, longBody);

        assertThat(lastBodySentTo("sara.ahmed")).hasSize(QUOTED.length() + " Nadia Ahmed wrote: ".length()
                + 200 + 3).endsWith("...");
    }

    @Test
    void anAccountThatHasBeenTurnedOffIsNotGivenMessagesItCannotRead() {
        AppUser disabled = mock(AppUser.class);
        when(disabled.isEnabled()).thenReturn(false);
        when(users.findByEmployeeId(1L)).thenReturn(Optional.of(disabled));

        notifier.repliedByDesk(ticket(null), adminOne, "Done.");

        verify(messages, never()).notifyUser(anyString(), any(), anyString(), anyString(), any());
    }
}
