package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.notification.NotificationEntry;
import com.example.leaveplatform.notification.NotificationKind;

/**
 * What the bell puts on the screen is Vaadin wiring, and a `RouterLink` cannot be
 * built without a running router, so the list of messages itself is checked on the
 * screen - see the bell rows in docs/TEST-PLAN.md. The two decisions behind that
 * list, how many to show and where a click goes, are here.
 */
class NotificationsBellTest {

    @Test
    void theBadgeSaysNothingWhenThereIsNothingToRead() {
        assertThat(NotificationsBell.badgeFor(0)).isEmpty();
    }

    @Test
    void theBadgeCountsUpToACeilingThatCannotWidenTheHeader() {
        assertThat(NotificationsBell.badgeFor(1)).isEqualTo("1");
        assertThat(NotificationsBell.badgeFor(99)).isEqualTo("99");
        assertThat(NotificationsBell.badgeFor(100)).isEqualTo("99+");
        assertThat(NotificationsBell.badgeFor(4127)).isEqualTo("99+");
    }

    /**
     * The person who asked follows their own requests, and the person a request
     * is waiting with goes to the queue - so a click lands where the message can
     * actually be acted on.
     */
    @Test
    void anApplicantsMessageOpensTheirOwnListAndEveryOtherOneOpensTheQueue() {
        assertThat(NotificationsBell.target(entry(NotificationKind.REQUEST_DECIDED)))
                .isEqualTo(MyLeaveView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.REQUEST_WAITING)))
                .isEqualTo(ApprovalsView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.REQUEST_RECOMMENDED)))
                .isEqualTo(ApprovalsView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.REQUEST_WITHDRAWN)))
                .isEqualTo(ApprovalsView.class);
    }

    /**
     * A ticket message carries a ticket number, not a leave request id, so a click
     * must not open /leave with somebody else's dates on it. The same reasoning
     * keeps a record-change message off the leave screens.
     */
    @Test
    void aMessageAboutSomethingOtherThanLeaveOpensTheScreenThatCanActOnIt() {
        assertThat(NotificationsBell.target(entry(NotificationKind.TICKET_WAITING)))
                .isEqualTo(TicketsView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.TICKET_ANSWERED)))
                .isEqualTo(TicketsView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.TICKET_CLOSED)))
                .isEqualTo(TicketsView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.PROFILE_WAITING)))
                .isEqualTo(AdminView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.PROFILE_APPROVED)))
                .isEqualTo(ProfileView.class);
        assertThat(NotificationsBell.target(entry(NotificationKind.PROFILE_REJECTED)))
                .isEqualTo(ProfileView.class);
    }

    private static NotificationEntry entry(NotificationKind kind) {
        return new NotificationEntry(1L, kind.name(), "Title", "Body", 2L, kind.screen(),
                Instant.now(), true);
    }
}
