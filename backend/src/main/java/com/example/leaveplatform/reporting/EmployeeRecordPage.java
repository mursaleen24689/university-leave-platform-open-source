package com.example.leaveplatform.reporting;

import java.time.LocalDate;
import java.util.List;
import com.example.leaveplatform.leave.LeaveStatus;

/**
 * One officer's leave record for one year, as a sheet the office prints and
 * files: a page of A4 upright per person, the year's balance figures above the
 * requests that produced them and a signature line below.
 *
 * <p>It follows the screen a head of department uses today, with two of its habits left
 * behind. The period is printed on the sheet, because a record with no year on
 * it cannot be put back in the right file. And the balance is labelled as the
 * day it was read rather than presented as the state of the last day of that
 * year, because the platform does not keep a snapshot of a balance and would be
 * inventing one.
 *
 * <p>The rows are requests, not days. A blank line is never a mark of presence,
 * the totals are added from the rows above them instead of from a second query,
 * and the page says all of that in its own words, so nobody signs a sheet that
 * quietly overstates what it knows.
 *
 * <p>Boxes are in millimetres, as on the application form, because that is the
 * only unit that means the same on every machine. Unlike the application form
 * this sheet has <em>no</em> fixed height: a person with thirty requests in a
 * year gets two pages of their own rather than a clipped one, since a box with
 * {@code overflow: hidden} loses the bottom of a list without telling anyone.
 */
final class EmployeeRecordPage {

    private EmployeeRecordPage() {
    }

    /**
     * A whole print run: the toolbar that never reaches the paper, then one
     * sheet per person in the order the office chose them.
     *
     * @param printedBy whose account produced the page, written on the paper
     *            because the audit entry and the sheet have to agree
     */
    static String of(List<EmployeeRecord> records, String printedBy, LocalDate onDay) {
        StringBuilder page = new StringBuilder(16384);
        page.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>")
                .append("<title>Leave records</title>")
                .append("<style>").append(css()).append("</style></head><body>");
        page.append("<div class=\"toolbar no-print\"><span>")
                .append(count(records.size())).append("</span>")
                .append("<button type=\"button\" onclick=\"window.print()\">Print</button>")
                .append("<span class=\"credit\">Prepared and Created by Sara Ahmed · "
                        + "Copyright © the institution, 2026</span>")
                .append("</div>");
        for (EmployeeRecord record : records) {
            page.append(sheet(record, printedBy, onDay));
        }
        return page.append("</body></html>").toString();
    }

    static String count(int records) {
        if (records == 1) {
            return "One leave record, ready to print.";
        }
        return records + " leave records, one sheet per person.";
    }

    /** What the rows above add up to, worked here so the page cannot disagree with itself. */
    private static String totals(EmployeeRecord record) {
        int approved = 0;
        int waiting = 0;
        int earnedBack = 0;
        int refused = 0;
        int withdrawn = 0;
        for (EmployeeRecord.Entry entry : record.entries()) {
            if (entry.status() == LeaveStatus.APPROVED) {
                approved += entry.days();
                if (entry.compensatory()) {
                    earnedBack += entry.days();
                }
            } else if (entry.waiting()) {
                waiting += entry.days();
            } else if (entry.status() == LeaveStatus.REJECTED) {
                refused++;
            } else {
                withdrawn++;
            }
        }
        if (record.entries().isEmpty()) {
            return "<p class=\"totals\">No leave request was made for " + record.year()
                    + ", so nothing was spent from the year.</p>";
        }
        StringBuilder out = new StringBuilder("<p class=\"totals\"><b>")
                .append(approved).append(approved == 1 ? "</b> day approved" : "</b> days approved")
                .append(" and <b>").append(waiting)
                .append(waiting == 1 ? "</b> day still waiting" : "</b> days still waiting")
                .append(" out of <b>").append(record.entries().size())
                .append(record.entries().size() == 1 ? "</b> request" : "</b> requests");
        if (refused > 0) {
            out.append(", <b>").append(refused).append("</b> refused");
        }
        if (withdrawn > 0) {
            out.append(", <b>").append(withdrawn).append("</b> withdrawn");
        }
        out.append(".");
        if (earnedBack > 0) {
            out.append(" <b>").append(earnedBack).append(earnedBack == 1 ? "</b> day" : "</b> days")
                    .append(" of the approved total were earned back by duty on an off day and came "
                            + "out of nothing.");
        }
        return out.append("</p>").toString();
    }

    private static String sheet(EmployeeRecord record, String printedBy, LocalDate onDay) {
        StringBuilder out = new StringBuilder(4096);
        out.append("<section class=\"sheet\">");
        out.append("<div class=\"head\"><img class=\"crest\" src=\"/logo.png\" alt=\"\"/>")
                .append("<div class=\"title\">")
                .append("<h1>the institution of Engineering &amp; Technology</h1>")
                .append("<h2>Department of ").append(Paper.esc(record.department())).append("</h2>")
                .append("<h3>LEAVE RECORD &mdash; ").append(record.year()).append("</h3>")
                .append("</div><div class=\"marks\"><p class=\"no\">Staff number<br/><b>")
                .append(Paper.esc(Paper.blank(record.employeeNo()))).append("</b></p>")
                .append("<p class=\"when\">Year ").append(record.year())
                .append("<br/>printed ").append(Paper.day(onDay)).append("</p></div></div>");

        out.append("<table class=\"who\"><tbody>");
        who(out, "Name of officer", record.name());
        who(out, "Designation", record.designation());
        who(out, "Department", record.department());
        out.append("</tbody></table>");

        out.append("<div class=\"balance\"><div><b>").append(record.allocated())
                .append("</b><span>days granted for ").append(record.year()).append("</span></div>")
                .append("<div><b>").append(record.used()).append("</b><span>days spent</span></div>")
                .append("<div><b>").append(record.remaining()).append("</b><span>days left</span></div>")
                .append("</div>");
        out.append("<p class=\"asof\">The three figures above are this year's balance as it stood on ")
                .append(Paper.day(onDay))
                .append(", the day this page was printed, not as it stood on 31 December ")
                .append(record.year())
                .append(". Public holidays are not leave days; weekends are counted, as they always were, ")
                .append("so a request that spans a Saturday is charged for it.</p>");

        out.append("<table class=\"log\"><thead><tr><th>Request</th><th>From</th><th>To</th>")
                .append("<th class=\"c\">Days</th><th>Status</th><th class=\"c\">Decided</th>")
                .append("<th class=\"c\">Duty off</th></tr></thead><tbody>");
        if (record.entries().isEmpty()) {
            out.append("<tr class=\"none\"><td colspan=\"7\">Nothing asked for this year</td></tr>");
        }
        for (EmployeeRecord.Entry entry : record.entries()) {
            out.append("<tr").append(entry.waiting() ? " class=\"waiting\"" : "").append(">")
                    .append("<td class=\"ref\">").append(Paper.esc(entry.reference())).append("</td>")
                    .append("<td>").append(Paper.day(entry.startDate())).append("</td>")
                    .append("<td>").append(Paper.day(entry.endDate())).append("</td>")
                    .append("<td class=\"c\">").append(entry.days()).append("</td>")
                    .append("<td>").append(Paper.esc(entry.status().label()))
                    .append("</td><td class=\"c\">").append(Paper.day(entry.decidedOn()))
                    .append("</td><td class=\"c\">").append(entry.compensatory() ? "Yes" : "&mdash;")
                    .append("</td></tr>");
        }
        out.append("</tbody></table>");

        out.append(totals(record));
        out.append("<div class=\"sign\"><div><span>Signature of officer</span></div>")
                .append("<div class=\"date\">Date: ").append(Paper.day(onDay)).append("</div></div>")
                .append("<div class=\"sign\"><div><span>Chairman / Head of Department</span>")
                .append("</div></div>");
        out.append("<p class=\"printed\">Printed from the leave platform by ")
                .append(Paper.esc(printedBy)).append(" on ").append(Paper.day(onDay))
                .append(". This sheet lists leave requests; it is not an attendance register, ")
                .append("and a day with no line on it is a day nothing was asked for.")
                .append("</p></section>");
        return out.toString();
    }

    private static void who(StringBuilder out, String label, String value) {
        out.append("<tr><th>").append(Paper.esc(label)).append("</th><td>")
                .append(Paper.esc(Paper.blank(value))).append("</td></tr>");
    }

    /**
     * A4 upright is 210 by 297 millimetres; twelve all round leaves 186 by 273
     * for the sheet, which is how much an office page of this kind holds.
     */
    private static String css() {
        return """
                @page { size: A4 portrait; margin: 12mm; }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: "Times New Roman", Times, serif; font-size: 10.5pt;
                       color: #000; print-color-adjust: exact; -webkit-print-color-adjust: exact; }
                .toolbar { display: flex; gap: 8px; align-items: center; padding: 10px 14px;
                           background: #f2f4f7; font-family: system-ui, sans-serif; font-size: 14px; }
                .toolbar button { padding: 6px 14px; }
                .toolbar .credit { margin-left: auto; color: #556070; font-size: 12px; }
                .sheet { width: 186mm; margin: 0 auto 8mm; break-after: page; padding: 0; }
                .sheet:last-child { break-after: auto; }
                .head { display: flex; gap: 4mm; align-items: flex-start;
                        border-bottom: 0.4mm solid #000; padding-bottom: 2mm; }
                .head .crest { width: 17mm; height: 17mm; object-fit: contain; }
                .head .title { flex: 1; text-align: center; }
                .head h1 { margin: 0; font-size: 13pt; }
                .head h2 { margin: 0.8mm 0 0; font-size: 11pt; }
                .head h3 { margin: 1.5mm 0 0; font-size: 11.5pt; text-decoration: underline; }
                .marks { width: 42mm; text-align: right; font-size: 8.5pt; }
                .marks .no { margin: 0; }
                .marks .when { margin: 1.5mm 0 0; }
                table.who { width: 100%; margin-top: 3mm; border-collapse: collapse; font-size: 10.5pt; }
                table.who th { width: 38mm; text-align: left; font-weight: normal; padding: 1mm 0; }
                table.who td { border-bottom: 0.25mm solid #000; padding: 1mm; }
                .balance { display: flex; gap: 4mm; margin-top: 4mm; }
                .balance div { flex: 1; border: 0.35mm solid #000; padding: 2mm; text-align: center; }
                .balance b { display: block; font-size: 15pt; }
                .balance span { display: block; font-size: 8.5pt; margin-top: 1mm; }
                .asof { margin: 2mm 0 0; font-size: 8pt; text-align: justify; color: #333; }
                table.log { width: 100%; margin-top: 4mm; border-collapse: collapse; font-size: 9.5pt; }
                table.log th { border: 0.25mm solid #000; background: #eeeeee; padding: 1mm;
                               font-size: 9pt; text-align: left; }
                table.log th.c { text-align: center; }
                table.log td { border: 0.25mm solid #000; padding: 1mm; vertical-align: top; }
                table.log td.c { text-align: center; }
                table.log td.ref { font-weight: 600; white-space: nowrap; }
                table.log tr.waiting td { background: #f7f7f7; }
                table.log tr.none td { text-align: center; font-style: italic; padding: 4mm 1mm; }
                .totals { margin: 3mm 0 0; font-size: 9.5pt; text-align: justify; }
                .sign { display: flex; justify-content: space-between; align-items: flex-end;
                        margin-top: 10mm; font-size: 9.5pt; }
                .sign > div > span { border-bottom: 0.3mm solid #000; padding: 0 30mm 0.6mm 0; }
                .sign .date { font-size: 9pt; }
                .printed { margin-top: 5mm; font-size: 7.5pt; color: #444; text-align: justify; }
                @media print { .no-print { display: none !important; } }
                """;
    }
}
