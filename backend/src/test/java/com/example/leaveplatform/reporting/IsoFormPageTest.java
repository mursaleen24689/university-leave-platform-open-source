package com.example.leaveplatform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The printed form read as paper, not as pixels: what the university's wording
 * says, which halves of a sentence are struck once a decision exists, who signs
 * a department head's own leave, and how the sheet is measured.
 */
class IsoFormPageTest {

    private static final String DEPARTMENT = "Computer and Information Systems Engineering";
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final String CODE = "0123456789ab";

    @Test
    void twoFormsShareOneSheetAndAreCutApart() {
        String page = IsoFormPage.of(List.of(plain("LV-000111"), plain("LV-000112")),
                "report.reader", MONDAY);

        assertThat(occurrences(page, "<section class=\"sheet\">")).isEqualTo(1);
        assertThat(occurrences(page, "<article class=\"form\">")).isEqualTo(2);
        assertThat(page).contains("Cut Here");
        assertThat(page).doesNotContain("class=\"blank\"");
    }

    @Test
    void anOddLastFormKeepsItsHalfOfTheSheet() {
        String page = IsoFormPage.of(
                List.of(plain("LV-000111"), plain("LV-000112"), plain("LV-000113")),
                "report.reader", MONDAY);

        // The spare half is left in on purpose: without it a browser may pull
        // the next sheet's first form onto this page.
        assertThat(occurrences(page, "<section class=\"sheet\">")).isEqualTo(2);
        assertThat(occurrences(page, "<article class=\"form\">")).isEqualTo(3);
        assertThat(occurrences(page, "class=\"blank\"")).isEqualTo(1);
    }

    @Test
    void thePaperSaysWhatTheUniversitysFormSays() {
        String page = IsoFormPage.of(List.of(plain("LV-000111")), "report.reader", MONDAY);

        assertThat(page).contains("F/SOP/REG 01/21/02")
                .contains("the institution of Engineering &amp; Technology")
                .contains("Department of " + DEPARTMENT)
                .contains("APPLICATION FORM FOR CASUAL LEAVE")
                .contains("Name of Applicant").contains("Designation").contains("Staff Number")
                .contains("Leave required").contains("From: 2 Nov 2026").contains("To: 6 Nov 2026")
                .contains("Days: 3").contains("Reason for Leave").contains("Family event")
                .contains("Casual leave required for reasons other than sickness shall not be availed"
                        + " without prior sanction")
                .contains("Medical Certificate").contains("Signature of Applicant")
                .contains("For Office Use Only").contains("Chairman / Head of Department");
    }

    @Test
    void aRequestNobodyHasDecidedPrintsBothHalvesOfTheSentenceWhole() {
        String page = IsoFormPage.of(List.of(plain("LV-000111")), "report.reader", MONDAY);

        assertThat(page).contains("may be / may not be")
                .contains("Recommended / Not Recommended")
                .contains("Status: Waiting for HOD")
                .doesNotContain("<s>")
                // Nothing on an undecided form may look decided, so both halves
                // of every sentence stay whole and no box is pre-ticked.
                .doesNotContain("[X]");
    }

    @Test
    void anApprovedRequestHasTheRefusedHalfStruckOut() {
        String page = IsoFormPage.of(List.of(asked("LV-000111", Boolean.TRUE)), "report.reader", MONDAY);

        assertThat(page).contains("<span class=\"yes\">may be</span> / <s>may not be</s>")
                .contains("<span class=\"yes\">Recommended</span> / <s>Not Recommended</s>")
                .contains("Status: Approved");
    }

    @Test
    void aRejectedRequestHasTheSanctionedHalfStruckOut() {
        String page = IsoFormPage.of(List.of(asked("LV-000111", Boolean.FALSE)), "report.reader", MONDAY);

        assertThat(page).contains("<s>may be</s> / <span class=\"no\">may not be</span>")
                .contains("<s>Recommended</s> / <span class=\"no\">Not Recommended</span>")
                .contains("Status: Rejected");
    }

    @Test
    void aDepartmentHeadIsSanctionedByTheDeanAndNotByTheirOwnChairman() {
        String page = IsoFormPage.of(List.of(head(Boolean.FALSE)), "report.reader", MONDAY);

        assertThat(page).contains("Dean CPE").contains("Recommendation of the office")
                .contains("Designation <b>HOD &amp; CHAIRMAN</b>")
                .doesNotContain("Chairman / Head of Department")
                .contains("Leave Sanctioned").contains("Not Sanctioned");
        // The Dean is asked to tick, and the answer the platform already holds
        // is on the paper so that the same decision is not signed twice.
        assertThat(occurrences(page, "[X]")).isEqualTo(1);
        assertThat(occurrences(page, "[  ]")).isEqualTo(1);
    }

    @Test
    void aHeadOfDepartmentWithNoDecisionYetLeavesBothBoxesEmpty() {
        String page = IsoFormPage.of(List.of(head(null)), "report.reader", MONDAY);

        assertThat(occurrences(page, "[  ]")).isEqualTo(2);
        assertThat(page).doesNotContain("[X]").doesNotContain("<s>");
    }

    @Test
    void aReasonTypedBySomeoneElseNeverBecomesMarkup() {
        IsoForm typed = new IsoForm("LV-000111", CODE, payload("LV-000111"), DEPARTMENT,
                "O'Brien \"Test\"", "Assistant Professor", "EMP-001", MONDAY, FRIDAY, 3,
                "<script>alert(1)</script>", MONDAY, "Approved", Boolean.TRUE, false,
                "<b>Supervisor</b>", MONDAY, null, null, false, 0);

        String page = IsoFormPage.of(List.of(typed), "report.reader", MONDAY);

        assertThat(page).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("O&#39;Brien &quot;Test&quot;")
                .contains("Recommended by: &lt;b&gt;Supervisor&lt;/b&gt;");
    }

    @Test
    void aFormWithACodeCarriesAPictureAndTheLettersUnderIt() {
        String page = IsoFormPage.of(List.of(plain("LV-000111")), "report.reader", MONDAY);

        assertThat(page).contains("<svg").contains("LV-000111 &middot; " + CODE)
                .doesNotContain("This server issues no verification codes.");
    }

    @Test
    void aFormWithoutACodeSaysSoInsteadOfPrintingAPictureNobodyCanCheck() {
        IsoForm unkeyed = asked("LV-000111", null);
        IsoForm noKey = new IsoForm(unkeyed.reference(), null, null, unkeyed.departmentName(),
                unkeyed.applicantName(), unkeyed.designation(), unkeyed.employeeNo(),
                unkeyed.startDate(), unkeyed.endDate(), unkeyed.days(), unkeyed.reason(),
                unkeyed.submittedOn(), unkeyed.statusLabel(), unkeyed.sanctioned(),
                unkeyed.headOfDepartment(), unkeyed.supervisorName(), unkeyed.recommendedOn(),
                unkeyed.decidedByName(), unkeyed.decidedOn(), unkeyed.compensatory(),
                unkeyed.previouslyAvailedDays());

        String page = IsoFormPage.of(List.of(noKey), "report.reader", MONDAY);

        // A QR that leads nowhere looks like proof without being any, so the
        // paper states the plain fact instead of one.
        assertThat(page).contains("This server issues no verification codes.")
                .doesNotContain("<svg");
    }

    @Test
    void theSheetIsA4SidewaysAndMeasuredInMillimetres() {
        String page = IsoFormPage.of(List.of(plain("LV-000111")), "report.reader", MONDAY);

        // Millimetres are the only unit that means the same at every desk, and
        // two 135-millimetre forms fit the 287 that A4 sideways leaves after the
        // margins - the university's own printer driver does the rest.
        assertThat(page).contains("@page { size: A4 landscape; margin: 5mm; }")
                .contains("width: 287mm").contains("height: 200mm")
                .contains("width: 135mm; height: 190mm")
                .contains("break-after: page");
    }

    @Test
    void theToolbarAndItsCreditNeverReachThePaper() {
        String page = IsoFormPage.of(List.of(plain("LV-000111")), "report.reader", MONDAY);

        assertThat(page).contains("class=\"toolbar no-print\"").contains("window.print()")
                .contains("Prepared and Created by Sara Ahmed")
                .contains("@media print { .no-print { display: none !important; } }");
        assertThat(page.indexOf("Prepared and Created by"))
                .isLessThan(page.indexOf("<section class=\"sheet\">"));
    }

    @Test
    void aCompensatoryFormSaysTheYearsBalanceWasNotSpent() {
        IsoForm earned = asked("LV-000111", Boolean.TRUE);
        IsoForm compensatory = new IsoForm(earned.reference(), earned.verificationCode(),
                earned.verificationPayload(), earned.departmentName(), earned.applicantName(),
                earned.designation(), earned.employeeNo(), earned.startDate(), earned.endDate(),
                earned.days(), earned.reason(), earned.submittedOn(), earned.statusLabel(),
                earned.sanctioned(), earned.headOfDepartment(), earned.supervisorName(),
                earned.recommendedOn(), earned.decidedByName(), earned.decidedOn(), true,
                earned.previouslyAvailedDays());

        String page = IsoFormPage.of(List.of(compensatory, plain("LV-000112")),
                "report.reader", MONDAY);

        assertThat(occurrences(page, "Compensatory - against duty on an off day.")).isEqualTo(1);
    }

    @Test
    void thePaperRecordsWhoPrintedItAndOnWhatDay() {
        String page = IsoFormPage.of(List.of(plain("LV-000111")), "sara.ahmed", MONDAY.plusDays(4));

        // The sheet and the audit entry have to agree about who made it, so the
        // same sentence a reader would search for is on the paper.
        assertThat(page).contains("Printed from the leave platform by sara.ahmed on 6 Nov 2026");
    }

    @Test
    void anAbsentStaffNumberOrReasonPrintsAsSpaceNotAsTheWordNull() {
        IsoForm blank = new IsoForm("LV-000111", null, null, DEPARTMENT, "Test Employee", null, null,
                MONDAY, FRIDAY, 3, null, null, "Waiting for HOD", null, false, null, null, null,
                null, false, 0);

        String page = IsoFormPage.of(List.of(blank), "report.reader", MONDAY);

        assertThat(page).doesNotContain("null");
        // Designation, staff number and the reason: three empty cells, one per
        // box a person would otherwise find the word "null" written in.
        assertThat(occurrences(page, "<td></td>")).isEqualTo(3);
    }

    @Test
    void oneDayAlreadySpentReadsAsOneDayNotAsOneDays() {
        IsoForm one = asked("LV-000111", null);
        IsoForm single = new IsoForm(one.reference(), one.verificationCode(),
                one.verificationPayload(), one.departmentName(), one.applicantName(),
                one.designation(), one.employeeNo(), one.startDate(), one.endDate(), one.days(),
                one.reason(), one.submittedOn(), one.statusLabel(), one.sanctioned(),
                one.headOfDepartment(), one.supervisorName(), one.recommendedOn(),
                one.decidedByName(), one.decidedOn(), one.compensatory(), 1);

        String page = IsoFormPage.of(List.of(single, one), "report.reader", MONDAY);

        assertThat(page).contains("Leave availed previously: 1 day.")
                .contains("Leave availed previously: 2 days.");
    }

    @Test
    void countingThePileReadsLikePaper() {
        assertThat(IsoFormPage.count(1)).isEqualTo("One application form, ready to print.");
        assertThat(IsoFormPage.count(2)).isEqualTo("2 application forms, 1 sheet of A4 sideways.");
        assertThat(IsoFormPage.count(3)).isEqualTo("3 application forms, 2 sheets of A4 sideways.");
    }

    @Test
    void aReasonThatFitsThePaperPrintsEveryWordOfIt() {
        IsoForm shortNote = written("LV-000111", "Attending my sister's wedding out of town.");

        assertThat(IsoFormPage.of(List.of(shortNote), "report.reader", MONDAY))
                .contains("<tr class=\"tall\"><th>Reason for Leave</th><td>Attending my sister&#39;s "
                        + "wedding out of town.</td>")
                .doesNotContain("printed in full in the leave register");
    }

    @Test
    void aReasonTooLongForThePaperStopsAtAWholeWordAndNamesWhereTheRestIs() {
        String typed = "Attending family functions out of town and then travelling to the city for "
                .repeat(20);
        String page = IsoFormPage.of(List.of(written("LV-000111", typed)), "report.reader", MONDAY);
        String printed = page.substring(page.indexOf("<tr class=\"tall\"><th>Reason for Leave</th><td>")
                + "<tr class=\"tall\"><th>Reason for Leave</th><td>".length());
        printed = printed.substring(0, printed.indexOf("</td>"));

        assertThat(printed).endsWith("... printed in full in the leave register under this number.");
        assertThat(printed).hasSizeLessThanOrEqualTo(IsoFormPage.REASON_ON_PAPER
                + "... printed in full in the leave register under this number.".length());
        assertThat(printed).doesNotContain(typed.strip());
        assertThat(occurrences(printed, "family functions")).isLessThan(20);

        String kept = printed.substring(0, printed.indexOf("..."));
        assertThat(typed).startsWith(kept);
        assertThat(kept).doesNotEndWith(" ");
        assertThat(typed.charAt(kept.length())).isEqualTo(' ');
    }

    @Test
    void aReasonWrittenWithoutASingleSpaceIsStillCutAndStillWraps() {
        IsoForm unbroken = written("LV-000111", "a".repeat(1000));

        String page = IsoFormPage.of(List.of(unbroken), "report.reader", MONDAY);

        assertThat(occurrences(page, "a".repeat(IsoFormPage.REASON_ON_PAPER))).isEqualTo(1);
        assertThat(page).doesNotContain("a".repeat(IsoFormPage.REASON_ON_PAPER + 1));
        assertThat(page).contains("overflow-wrap: anywhere");
    }

    /** The same request with whatever the applicant typed as their reason. */
    private static IsoForm written(String reference, String reason) {
        IsoForm ordinary = asked(reference, null);
        return new IsoForm(ordinary.reference(), ordinary.verificationCode(),
                ordinary.verificationPayload(), ordinary.departmentName(), ordinary.applicantName(),
                ordinary.designation(), ordinary.employeeNo(), ordinary.startDate(), ordinary.endDate(),
                ordinary.days(), reason, ordinary.submittedOn(), ordinary.statusLabel(),
                ordinary.sanctioned(), ordinary.headOfDepartment(), ordinary.supervisorName(),
                ordinary.recommendedOn(), ordinary.decidedByName(), ordinary.decidedOn(),
                ordinary.compensatory(), ordinary.previouslyAvailedDays());
    }

    /** One ordinary request from an ordinary employee, with a code beside it. */
    private static IsoForm plain(String reference) {
        return asked(reference, null);
    }

    private static IsoForm asked(String reference, Boolean sanctioned) {
        return new IsoForm(reference, CODE, payload(reference), DEPARTMENT, "Test Employee",
                "Assistant Professor", "EMP-001", MONDAY, FRIDAY, 3, "Family event",
                MONDAY.minusDays(2), label(sanctioned), sanctioned, false, "Bilal Ahmed",
                MONDAY.minusDays(1), sanctioned == null ? null : "Aisha Rahman",
                sanctioned == null ? null : MONDAY, false, 2);
    }

    /** A department head's own leave, which the Dean rather than the chairman sanctions. */
    private static IsoForm head(Boolean sanctioned) {
        IsoForm ordinary = asked("LV-000111", sanctioned);
        return new IsoForm(ordinary.reference(), ordinary.verificationCode(),
                ordinary.verificationPayload(), ordinary.departmentName(), ordinary.applicantName(),
                "HOD & CHAIRMAN", ordinary.employeeNo(), ordinary.startDate(), ordinary.endDate(),
                ordinary.days(), ordinary.reason(), ordinary.submittedOn(), ordinary.statusLabel(),
                ordinary.sanctioned(), true, ordinary.supervisorName(), ordinary.recommendedOn(),
                ordinary.decidedByName(), ordinary.decidedOn(), ordinary.compensatory(),
                ordinary.previouslyAvailedDays());
    }

    private static String payload(String reference) {
        return VerificationCode.MARKER + "|" + reference + "|" + CODE;
    }

    private static String label(Boolean sanctioned) {
        return sanctioned == null ? "Waiting for HOD" : sanctioned ? "Approved" : "Rejected";
    }

    private static int occurrences(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            found++;
        }
        return found;
    }
}
