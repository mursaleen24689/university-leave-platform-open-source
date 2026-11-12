package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveStatus;

/**
 * The screen never builds the file itself: it hands the person an address that
 * asks the reporting endpoint the same questions the list above was built with.
 * These tests keep that promise, because a download that silently drops a filter
 * would be a different report from the one on the screen.
 */
class ReportsViewTest {

    @Test
    void anUnnarrowedListAsksForTheWholeReport() {
        assertThat(ReportsView.registerHref(null, LeaveFilter.any()))
                .isEqualTo("/api/v1/reports/register/csv?");
    }

    @Test
    void anHeadOfDepartmentNeverNamesADepartmentInTheFileAddress() {
        // The service fixes an HOD to their own department whatever the address
        // says, so putting one in it would only invite a refusal.
        assertThat(ReportsView.registerHref(null, LeaveFilter.any()))
                .doesNotContain("departmentId");
        assertThat(ReportsView.summaryHref(null, 2026)).doesNotContain("departmentId");
    }

    @Test
    void theDepartmentAnAdministratorChoseTravelsToTheFile() {
        assertThat(ReportsView.registerHref(7L, LeaveFilter.any())).contains("departmentId=7");
        assertThat(ReportsView.summaryHref(7L, 2026)).contains("departmentId=7&year=2026");
    }

    @Test
    void thePeriodAndStatusOnTheScreenTravelToTheFile() {
        LeaveFilter period = new LeaveFilter(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31), LeaveStatus.APPROVED);

        String address = ReportsView.registerHref(null, period);

        // ISO dates and the name of the status: what the endpoint reads back.
        assertThat(address).contains("from=2026-01-01", "to=2026-03-31", "status=APPROVED");
    }

    @Test
    void onlyThePartOfThePeriodThatWasFilledInTravels() {
        String address = ReportsView.registerHref(null,
                new LeaveFilter(LocalDate.of(2026, 6, 1), null, null));

        assertThat(address).contains("from=2026-06-01").doesNotContain("to=", "status=");
    }

    @Test
    void theSummaryAlwaysNamesTheYearItWasBuiltFor() {
        assertThat(ReportsView.summaryHref(null, 2025)).endsWith("year=2025");
    }

    @Test
    void theAttendanceFileAsksForTheMonthOnTheScreen() {
        assertThat(ReportsView.attendanceHref(7L, 2026, 3))
                .isEqualTo("/api/v1/reports/attendance/csv?departmentId=7&year=2026&month=3");
        assertThat(ReportsView.attendanceHref(null, 2026, 11)).endsWith("year=2026&month=11");
    }

    @Test
    void aMonthIsOfferedByItsNameNotItsNumber() {
        assertThat(ReportsView.monthName(11)).isEqualTo("November");
        assertThat(ReportsView.monthName(1)).isEqualTo("January");
        assertThat(ReportsView.monthName(null)).isEmpty();
    }

    @Test
    void thePrintButtonNamesTheRowsItTickedAndNothingElse() {
        // The paper a person gets has to be the paper they chose on the list, so
        // the address carries the chosen numbers in the chosen order.
        assertThat(ReportsView.isoFormHref(List.of(7L, 9L, 11L)))
                .isEqualTo("/api/v1/reports/iso-form?ids=7,9,11");
        assertThat(ReportsView.isoFormHref(List.of(4L))).endsWith("ids=4");
    }

    @Test
    void aPrintedRecordCarriesTheNamesTickedAndTheYearTheListWasBuiltFor() {
        // The sheet states one year on the paper, so the year the summary was
        // read for travels with it: a record printed from a list showing 2025
        // must not arrive as the running year.
        assertThat(ReportsView.recordHref(List.of(3L, 5L), 2025))
                .isEqualTo("/api/v1/reports/employee-record?year=2025&ids=3,5");
        assertThat(ReportsView.recordHref(List.of(3L), 2026)).endsWith("year=2026&ids=3");
    }
}
