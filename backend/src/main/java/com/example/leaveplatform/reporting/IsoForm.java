package com.example.leaveplatform.reporting;

import java.time.LocalDate;

/**
 * Everything one printed application form states, as the platform knows it.
 *
 * <p>It is a separate type from a leave request on purpose: {@link IsoFormPage}
 * has to be testable with a hand-built record instead of a database, and the
 * wording on paper is a document of the university, not a dump of columns.
 *
 * @param reference the number both sides quote, for example {@code LV-000123}
 * @param verificationCode the code printed as letters, or null when this server
 *            issues none because {@code LEAVE_VERIFY_KEY} is not set
 * @param verificationPayload what the QR picture holds - an address when
 *            {@code app.public-url} is configured, the two parts as words when
 *            it is not - or null with no code
 * @param departmentName the department the applicant belongs to
 * @param applicantName the applicant's own name
 * @param designation as the staff record gives it
 * @param employeeNo the staff number, which may still be blank while records are completed
 * @param startDate first day asked for
 * @param endDate last day asked for
 * @param days working days counted by the platform, holidays left out
 * @param reason the applicant's own words
 * @param submittedOn the day the request was made
 * @param statusLabel the waiting or decision word, in the applicant's language
 * @param sanctioned the decision, or null while nobody has decided yet
 * @param headOfDepartment whether the applicant heads the department whose
 *            decision this is, which decides who signs the paper
 * @param supervisorName who recommended the request, or null when there was no
 *            supervisor step
 * @param recommendedOn the day of that recommendation
 * @param decidedByName who took the final decision
 * @param decidedOn the day of it
 * @param compensatory whether the days were earned back by duty on a holiday
 * @param previouslyAvailedDays approved days already spent in the year, not
 *            counting the request being printed
 */
public record IsoForm(String reference, String verificationCode, String verificationPayload,
        String departmentName, String applicantName, String designation, String employeeNo,
        LocalDate startDate, LocalDate endDate, int days, String reason, LocalDate submittedOn,
        String statusLabel, Boolean sanctioned, boolean headOfDepartment, String supervisorName,
        LocalDate recommendedOn, String decidedByName, LocalDate decidedOn, boolean compensatory,
        int previouslyAvailedDays) {
}
