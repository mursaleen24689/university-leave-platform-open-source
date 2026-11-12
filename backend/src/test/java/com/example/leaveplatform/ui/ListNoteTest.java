package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveService;
import com.example.leaveplatform.leave.LeaveStatus;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.reporting.AttendanceSheet;
import com.example.leaveplatform.reporting.ReportService;

/**
 * The line above a capped list is the only thing telling a person that the
 * server stopped counting, so its wording is worth a test of its own.
 */
class ListNoteTest {

    @Test
    void aListThatWasNotCutShortSaysNothingAboutIt() {
        assertThat(ListNote.ofLeave(3, LeaveFilter.any())).isEqualTo("3 requests.");
        assertThat(ListNote.ofLeave(1, LeaveFilter.any())).isEqualTo("1 request.");
    }

    @Test
    void thePeriodThePersonChosenIsNamed() {
        LeaveFilter period = new LeaveFilter(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31), LeaveStatus.APPROVED);

        assertThat(ListNote.ofLeave(12, period))
                .isEqualTo("12 requests matching from 2026-01-01 to 2026-03-31, approved.");
    }

    @Test
    void aCutShortLeaveListTellsThemHowToSeeTheRest() {
        String note = ListNote.ofLeave(LeaveService.LIST_LIMIT, LeaveFilter.any());

        assertThat(note).contains("These are the newest " + LeaveService.LIST_LIMIT)
                .contains("Narrow the dates to see the rest.");
    }

    @Test
    void aCutShortRegisterPointsAtTheFileInstead() {
        String note = ListNote.ofRegister(ReportService.SCREEN_LIMIT, LeaveFilter.any());

        assertThat(note).contains("download the file, which is not cut short.")
                .doesNotContain("Narrow the dates to see the rest");
    }

    @Test
    void aShortRegisterReadsExactlyLikeTheLeaveList() {
        assertThat(ListNote.ofRegister(4, LeaveFilter.any())).isEqualTo("4 requests.");
    }

    @Test
    void theSummaryLineCountsPeopleAndNamesTheYear() {
        assertThat(ListNote.ofSummary(1, 2026)).isEqualTo("1 employee for 2026.");
        assertThat(ListNote.ofSummary(27, 2026)).isEqualTo("27 employees for 2026.");
    }

    @Test
    void theAttendanceLineNamesTheMonthAndNeverSpeaksOfACutOff() {
        AttendanceSheet sheet = new AttendanceSheet(2026, 11, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), List.of());

        assertThat(ListNote.ofSheet(1, sheet)).isEqualTo("1 employee for November 2026.");
        assertThat(ListNote.ofSheet(412, sheet)).isEqualTo("412 employees for November 2026.");
    }

    @Test
    void theBellLineCountsWhatStillNeedsReading() {
        assertThat(ListNote.ofMessages(0, 0)).isEqualTo("No messages.");
        assertThat(ListNote.ofMessages(1, 1)).isEqualTo("1 message listed, 1 unread.");
        assertThat(ListNote.ofMessages(12, 3)).isEqualTo("12 messages listed, 3 unread.");
    }

    @Test
    void aFullBellSaysTheOlderMessagesAreStillThere() {
        assertThat(ListNote.ofMessages(NotificationService.LIST_LIMIT, 2)).endsWith(
                "These are the newest 200 - marking some read lets the older ones show.");
        assertThat(ListNote.ofMessages(NotificationService.LIST_LIMIT - 1, 0))
                .doesNotContain("newest");
    }
}
