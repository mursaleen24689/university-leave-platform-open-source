package com.example.leaveplatform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.leave.LeaveStatus;

/**
 * The printed leave record read as paper: whose year it says it is, which
 * figures it states and when they were read, what a waiting request looks like
 * beside an approved one, and what never reaches the sheet.
 */
class EmployeeRecordPageTest {

    private static final String DEPARTMENT = "Computer and Information Systems Engineering";
    private static final LocalDate PRINTED = LocalDate.of(2026, 11, 2);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);
    private static final int YEAR = 2026;

    @Test
    void oneSheetPerPageInTheOrderTheOfficeChoseThem() {
        String page = EmployeeRecordPage.of(
                List.of(record(15, 4, entry("LV-000111", LeaveStatus.APPROVED, false, MONDAY),
                        entry("LV-000112", LeaveStatus.PENDING, false, null)),
                        record(8, 0)),
                "report.reader", PRINTED);

        assertThat(occurrences(page, "<section class=\"sheet\">")).isEqualTo(2);
        assertThat(occurrences(page, "Staff number")).isEqualTo(2);
        assertThat(occurrences(page, "LEAVE RECORD &mdash; 2026")).isEqualTo(2);
    }

    @Test
    void thePaperSaysWhoseYearItIsAndWhenItWasMade() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4)), "report.reader", PRINTED);

        assertThat(page).contains("the institution of Engineering &amp; Technology")
                .contains("Department of " + DEPARTMENT)
                .contains("LEAVE RECORD &mdash; " + YEAR)
                .contains("Name of officer").contains("Test Employee")
                .contains("Designation").contains("Assistant Professor")
                .contains("printed 2 Nov 2026")
                .contains("Printed from the leave platform by report.reader on 2 Nov 2026");
    }

    @Test
    void theThreeBalanceFiguresAreTheOnesThePlatformHolds() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4)), "report.reader", PRINTED);

        assertThat(page).contains("<b>15</b><span>days granted for 2026</span>")
                .contains("<b>4</b><span>days spent</span>")
                .contains("<b>11</b><span>days left</span>");
    }

    @Test
    void theBalanceStatesTheDayItWasReadRatherThanTheLastDayOfTheYear() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4)), "report.reader", PRINTED);

        // The platform keeps no snapshot of a balance, so a sheet that claimed to
        // show the position on 31 December would be inventing one.
        assertThat(page).contains("as it stood on 2 Nov 2026, the day this page was printed")
                .contains("not as it stood on 31 December 2026")
                // The sheet is signed, so what it says about the figures has to
                // be what LeaveDayCounter actually does: weekends are charged.
                .contains("Public holidays are not leave days; weekends are counted");
    }

    @Test
    void everyRequestOfTheYearIsOnTheSheetWhateverWasDecidedAboutIt() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4,
                entry("LV-000111", LeaveStatus.APPROVED, false, WEDNESDAY),
                entry("LV-000112", LeaveStatus.PENDING_SUPERVISOR, false, null),
                entry("LV-000113", LeaveStatus.REJECTED, false, WEDNESDAY))),
                "report.reader", PRINTED);

        assertThat(page).contains("LV-000111").contains("LV-000112").contains("LV-000113")
                .contains("Approved").contains("Waiting for supervisor").contains("Rejected")
                .contains("2 Nov 2026").contains("4 Nov 2026");
        // A waiting line is set apart but never hidden: dropping it would hand
        // somebody a sheet that reads as a clean year.
        assertThat(page).contains("class=\"waiting\"");
    }

    @Test
    void theTotalsAreAddedFromTheRowsPrintedAboveThem() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4,
                entry("LV-000111", LeaveStatus.APPROVED, false, WEDNESDAY),
                entry("LV-000112", LeaveStatus.APPROVED, false, WEDNESDAY),
                entry("LV-000113", LeaveStatus.PENDING, false, null),
                entry("LV-000114", LeaveStatus.REJECTED, false, WEDNESDAY))),
                "report.reader", PRINTED);

        assertThat(page).contains("<b>4</b> days approved and <b>2</b> days still waiting out of "
                + "<b>4</b> requests, <b>1</b> refused.");
        assertThat(page).doesNotContain("withdrawn");
    }

    @Test
    void aDayEarnedBackByDutyIsMarkedOnItsRowAndSaidBelowThem() {
        String page = EmployeeRecordPage.of(List.of(record(15, 0,
                entry("LV-000111", LeaveStatus.APPROVED, true, WEDNESDAY))),
                "report.reader", PRINTED);

        assertThat(page).contains("<td class=\"c\">Yes</td>")
                .contains("<b>2</b> days of the approved total were earned back by duty on an "
                        + "off day and came out of nothing.");
    }

    @Test
    void aQuietYearSaysSoRatherThanShowingAnEmptyTable() {
        String page = EmployeeRecordPage.of(List.of(record(15, 0)), "report.reader", PRINTED);

        assertThat(page).contains("Nothing asked for this year")
                .contains("No leave request was made for 2026, so nothing was spent from the year.");
    }

    @Test
    void theSheetCarriesNoReasonAndNoNameOfAnyoneWhoDecided() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4,
                entry("LV-000111", LeaveStatus.APPROVED, false, WEDNESDAY))),
                "report.reader", PRINTED);

        // The record answers "how many days, and what happened to them". Who
        // signed a decision and why the person asked are on the form and in the
        // register, and a sheet that goes into a file carries neither.
        assertThat(page).doesNotContain("Reason").doesNotContain("Family event")
                .doesNotContain("Recommended by").doesNotContain("Decided by:");
    }

    @Test
    void nothingTypedIntoAStaffRecordBecomesMarkup() {
        EmployeeRecord awkward = new EmployeeRecord("EMP-001", "Ms <script>evil</script>",
                "Dean \"A\" & B", DEPARTMENT, YEAR, 15, 4, List.of());

        String page = EmployeeRecordPage.of(List.of(awkward), "report.reader", PRINTED);

        assertThat(page).contains("Ms &lt;script&gt;evil&lt;/script&gt;")
                .contains("Dean &quot;A&quot; &amp; B")
                .doesNotContain("<script>evil</script>");
    }

    @Test
    void theToolbarThatNeverReachesThePaper() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4)), "report.reader", PRINTED);

        assertThat(page).contains("class=\"toolbar no-print\"")
                .contains("One leave record, ready to print.")
                .contains("window.print()")
                .contains("Prepared and Created by Sara Ahmed")
                .contains("@media print { .no-print { display: none !important; } }");
        assertThat(EmployeeRecordPage.count(3)).isEqualTo("3 leave records, one sheet per person.");
    }

    @Test
    void theSheetGrowsOverMorePagesInsteadOfClippingItsOwnList() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4)), "report.reader", PRINTED);

        // The application form's boxes are fixed in millimetres and clip what
        // will not fit, which is right for a form and wrong for a list: a person
        // with thirty requests in a year must get a second page, not a short one.
        assertThat(page).contains("@page { size: A4 portrait; margin: 12mm; }")
                .contains("break-after: page")
                .doesNotContain("overflow: hidden");
    }

    @Test
    void theSheetAsksForTwoSignaturesAndSaysWhatItIsNot() {
        String page = EmployeeRecordPage.of(List.of(record(15, 4)), "report.reader", PRINTED);

        assertThat(page).contains("Signature of officer")
                .contains("Chairman / Head of Department")
                .contains("it is not an attendance register")
                .contains("a day with no line on it is a day nothing was asked for");
    }

    private static EmployeeRecord record(int allocated, int used, EmployeeRecord.Entry... entries) {
        return new EmployeeRecord("EMP-001", "Test Employee", "Assistant Professor", DEPARTMENT,
                YEAR, allocated, used, List.of(entries));
    }

    private static EmployeeRecord.Entry entry(String reference, LeaveStatus status,
            boolean compensatory, LocalDate decidedOn) {
        return new EmployeeRecord.Entry(reference, MONDAY, WEDNESDAY, 2, status, compensatory,
                decidedOn);
    }

    private static int occurrences(String haystack, String needle) {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            found++;
        }
        return found;
    }
}
