package com.example.leaveplatform.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.organization.Department;

/**
 * The state machine on its own, without a database. These are the transitions a
 * person at the desk argues about, so they are stated once here: an answer does
 * not finish a ticket, and a reply from the person who asked does put it back in
 * the queue.
 */
class SupportTicketTest {

    private final Employee asker = mock(Employee.class);
    private final Employee handler = mock(Employee.class);
    private final Department department = mock(Department.class);

    private SupportTicket newTicket() {
        when(department.getName()).thenReturn("Centre for Quality");
        return new SupportTicket(asker, department, TicketCategory.LEAVE_RECORD,
                "My balance is short by two days");
    }

    @Test
    void aTicketStartsWaitingForTheDeskWithNoNameOnIt() {
        SupportTicket ticket = newTicket();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getStatus().awaitsDesk()).isTrue();
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void thereIsNoNumberUntilTheDatabaseHasGivenTheRowAnId() {
        SupportTicket ticket = newTicket();

        ticket.stampReference();
        assertThat(ticket.getReference()).isNull();
    }

    @Test
    void anAnswerLeavesTheTicketOpenForThePersonWhoAsked() {
        SupportTicket ticket = newTicket();
        ticket.assign(handler);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        ticket.answeredByDesk();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ANSWERED);
        assertThat(ticket.getStatus().awaitsDesk()).isFalse();
        assertThat(ticket.getStatus().isOpen()).isTrue();
    }

    @Test
    void aReplyFromThePersonWhoAskedGoesBackToWhoeverWasHandlingIt() {
        SupportTicket assigned = newTicket();
        assigned.assign(handler);
        assigned.answeredByDesk();
        assigned.answeredByStaff();
        assertThat(assigned.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        SupportTicket unassigned = newTicket();
        unassigned.answeredByDesk();
        unassigned.answeredByStaff();
        assertThat(unassigned.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void closingStampsTheTimeAndReopeningClearsIt() {
        SupportTicket ticket = newTicket();
        ticket.assign(handler);
        ticket.close();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(ticket.getStatus().isOpen()).isFalse();

        ticket.reopen();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void assigningAgainSimplyChangesWhoIsHandlingIt() {
        SupportTicket ticket = newTicket();
        Employee second = mock(Employee.class);

        ticket.assign(handler);
        ticket.assign(second);

        assertThat(ticket.getAssignee()).isSameAs(second);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }
}
