package com.example.leaveplatform.reporting;

import java.time.LocalDate;
import java.util.List;

/**
 * The university's casual leave application form as one printable page, two
 * forms to an A4 sheet turned sideways.
 *
 * <p>The wording and the measurements follow the paper the departments use
 * today, which the earlier application reproduced on screen: the form code in the
 * corner, the three-line title, the applicant's answers, the two notes about
 * prior sanction and a medical certificate, then the office half with the
 * recommendation and the decision. Two forms to a sheet is how the office
 * actually works - a supervisor brings a pile of requests and prints them to
 * sign - so the pairs are cut apart along the dashed line.
 *
 * <p>It differs from that paper in one respect, and that is the reason the
 * screen exists: the number in the corner is the request's own, {@code
 * LV-000123}, and under the picture is a code only this server can produce for
 * it. A form carried out of the department can therefore be proved or refused
 * from the number alone, years later, without telephoning anybody.
 *
 * <p>Everything here is one HTML document the browser prints, so the platform
 * needs no PDF library and the university's own printer driver does the
 * measuring. The boxes are stated in millimetres because that is the only unit
 * that means the same on every machine.
 */
final class IsoFormPage {

    /** The form's own number, printed where the office expects to find it. */
    static final String FORM_CODE = "F/SOP/REG 01/21/02";

    /** Two forms, then the pair is cut apart; a lone form still holds its half. */
    private static final int PER_SHEET = 2;

    /**
     * How much of a reason one form's paper can hold.
     *
     * <p>The box is 190 millimetres tall and everything under the reason - the
     * NOTE, the signature line, the office half with its QR - is measured to
     * the same page, so a reason longer than this would push that bottom half
     * past the border and the printer would silently drop it. A reason is
     * entered freely and may run to a thousand characters, so the paper hands
     * the rest over to the register instead of losing it.
     */
    static final int REASON_ON_PAPER = 380;

    private IsoFormPage() {
    }

    /**
     * A whole page: the toolbar that never reaches the paper, then one sheet
     * per pair of forms.
     *
     * @param forms the requests to print, in the order the office chose them
     * @param printedBy whose account produced the page, written on the paper
     *            because the audit entry and the sheet have to agree
     */
    static String of(List<IsoForm> forms, String printedBy, LocalDate onDay) {
        StringBuilder page = new StringBuilder(16384);
        page.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>")
                .append("<title>Casual leave application forms</title>")
                .append("<style>").append(css()).append("</style></head><body>");
        page.append("<div class=\"toolbar no-print\"><span>")
                .append(count(forms.size())).append("</span>")
                .append("<button type=\"button\" onclick=\"window.print()\">Print</button>")
                .append("<span class=\"credit\">Prepared and Created by Sara Ahmed · "
                        + "Copyright © the institution, 2026</span>")
                .append("</div>");
        for (int start = 0; start < forms.size(); start += PER_SHEET) {
            page.append("<section class=\"sheet\">");
            for (int slot = 0; slot < PER_SHEET; slot++) {
                int index = start + slot;
                if (index < forms.size()) {
                    if (slot > 0) {
                        page.append("<div class=\"cut\" aria-hidden=\"true\"><span>Cut Here</span></div>");
                    }
                    page.append(form(forms.get(index), printedBy, onDay));
                } else if (slot > 0) {
                    // The empty half of a pair. Without it the browser may pull
                    // the next sheet's first form onto this page.
                    page.append("<div class=\"blank\" aria-hidden=\"true\"></div>");
                }
            }
            page.append("</section>");
        }
        return page.append("</body></html>").toString();
    }

    static String count(int forms) {
        if (forms == 1) {
            return "One application form, ready to print.";
        }
        int sheets = (forms + PER_SHEET - 1) / PER_SHEET;
        return forms + " application forms, " + sheets + (sheets == 1 ? " sheet" : " sheets")
                + " of A4 sideways.";
    }

    /** The visible text of one form, as the university's paper reads it. */
    private static String form(IsoForm form, String printedBy, LocalDate onDay) {
        StringBuilder out = new StringBuilder(4096);
        out.append("<article class=\"form\">");
        out.append("<div class=\"head\"><img class=\"crest\" src=\"/logo.png\" alt=\"\"/>")
                .append("<div class=\"title\">")
                .append("<h1>the institution of Engineering &amp; Technology</h1>")
                .append("<h2>Department of ").append(esc(form.departmentName())).append("</h2>")
                .append("<h3>APPLICATION FORM FOR CASUAL LEAVE</h3>")
                .append("</div><div class=\"marks\"><p class=\"code\">").append(FORM_CODE)
                .append("</p><p class=\"ref\">").append(esc(form.reference())).append("</p>")
                .append(verification(form)).append("</div></div>");

        out.append("<table class=\"fields\"><tbody>");
        row(out, "Name of Applicant", form.applicantName());
        row(out, "Designation", form.designation());
        row(out, "Staff Number", form.employeeNo());
        out.append("<tr><th>Leave required</th>")
                .append("<td class=\"triple\"><span>From: ").append(day(form.startDate()))
                .append("</span><span>To: ").append(day(form.endDate()))
                .append("</span><span>Days: ").append(form.days()).append("</span></td></tr>");
        out.append("<tr class=\"tall\"><th>Reason for Leave</th><td>")
                .append(esc(printedReason(form.reason()))).append("</td></tr>");
        out.append("</tbody></table>");

        out.append("<p class=\"note\"><b>NOTE:</b> 1. Casual leave required for reasons other than "
                + "sickness shall not be availed without prior sanction. 2. Casual leave desired on "
                + "Medical grounds for more than Two days shall be sanctioned on the production of "
                + "Medical Certificate.</p>");

        out.append("<div class=\"sign\"><div><span>Signature of Applicant</span></div><div class=\"date\">")
                .append("Date: ").append(day(form.submittedOn())).append("</div></div>");

        out.append(officeUse(form));

        out.append("<p class=\"printed\">Printed from the leave platform by ")
                .append(esc(printedBy)).append(" on ").append(day(onDay)).append("</p>");
        if (form.compensatory()) {
            out.append("<p class=\"comp\">Compensatory - against duty on an off day. "
                    + "No days were spent from the year's balance.</p>");
        }
        return out.append("</article>").toString();
    }

    /**
     * The office half. A request that has not been decided yet prints the two
     * halves of the sentence whole, so the officer ticks one on paper; a decided
     * one has the refused half struck through, because the platform knows the
     * answer and the paper should not pretend otherwise.
     */
    private static String officeUse(IsoForm form) {
        StringBuilder out = new StringBuilder(1024);
        out.append("<section class=\"office\"><h4>For Office Use Only</h4>")
                .append("<p>Mr./Ms. <b>").append(esc(form.applicantName())).append("</b>")
                .append(" Designation <b>").append(esc(blank(form.designation())))
                .append("</b> ").append(sanctioned(form)).append(" sanctioned leave.</p>");
        int spent = form.previouslyAvailedDays();
        out.append("<p>Leave availed previously: ").append(spent)
                .append(spent == 1 ? " day." : " days.</p>");

        if (form.headOfDepartment()) {
            out.append("<p class=\"boxes\"><span>").append(ticked(form.sanctioned(), true,
                    "Leave Sanctioned")).append("</span><span>")
                    .append(ticked(form.sanctioned(), false, "Not Sanctioned")).append("</span></p>")
                    .append("<p class=\"lines\"><span>Recommendation of the office</span></p>")
                    .append("<div class=\"sign\"><div><span>Dean CPE</span></div></div>");
        } else {
            out.append("<p>").append(recommendation(form)).append("</p>")
                    .append("<div class=\"sign\"><div><span>Chairman / Head of Department</span></div>")
                    .append("<div class=\"date\">").append(decided(form)).append("</div></div>");
        }
        return out.append("</section>").toString();
    }

    private static String sanctioned(IsoForm form) {
        Boolean decided = form.sanctioned();
        if (decided == null) {
            return "may be / may not be";
        }
        return decided ? "<span class=\"yes\">may be</span> / <s>may not be</s>"
                : "<s>may be</s> / <span class=\"no\">may not be</span>";
    }

    private static String recommendation(IsoForm form) {
        Boolean decided = form.sanctioned();
        String words = decided == null ? "Recommended / Not Recommended"
                : decided ? "<span class=\"yes\">Recommended</span> / <s>Not Recommended</s>"
                        : "<s>Recommended</s> / <span class=\"no\">Not Recommended</span>";
        StringBuilder out = new StringBuilder(words);
        if (form.supervisorName() != null) {
            out.append("<br/>Recommended by: ").append(esc(form.supervisorName()));
            if (form.recommendedOn() != null) {
                out.append(" on ").append(day(form.recommendedOn()));
            }
        }
        return out.toString();
    }

    private static String decided(IsoForm form) {
        StringBuilder out = new StringBuilder("Status: ").append(esc(form.statusLabel()));
        if (form.decidedByName() != null) {
            out.append("<br/>Decided by: ").append(esc(form.decidedByName()));
            if (form.decidedOn() != null) {
                out.append(" on ").append(day(form.decidedOn()));
            }
        }
        return out.toString();
    }

    /**
     * A box the office can tick on paper. Once the platform holds the decision
     * the box is filled, because a form that asks for a second opinion on an
     * answered request is a form that gets signed twice.
     */
    private static String ticked(Boolean decided, boolean thisBox, String caption) {
        boolean mark = decided != null && decided == thisBox;
        return "<span class=\"box" + (mark ? " on" : "") + "\">" + (mark ? "[X]" : "[  ]")
                + "</span> " + esc(caption);
    }

    /**
     * The picture and the letters under it. With no key configured the form
     * says so in words instead of printing a picture nobody can check: a QR that
     * leads nowhere is worse than none, because it looks like proof.
     */
    private static String verification(IsoForm form) {
        if (form.verificationPayload() == null || form.verificationCode() == null) {
            return "<p class=\"nokey\">This server issues no verification codes.</p>";
        }
        return "<div class=\"qr\">" + QrGlyph.svg(form.verificationPayload(), 26)
                + "<p class=\"check\">" + esc(form.reference()) + " &middot; "
                + esc(form.verificationCode()) + "</p></div>";
    }

    private static void row(StringBuilder out, String label, String value) {
        out.append("<tr><th>").append(esc(label)).append("</th><td>")
                .append(esc(blank(value))).append("</td></tr>");
    }

    private static String day(LocalDate day) {
        return Paper.day(day);
    }

    private static String blank(String value) {
        return Paper.blank(value);
    }

    /**
     * The reason as it can stand on the paper.
     *
     * <p>Words are never cut in half: the last whole one inside the limit is
     * where the paper stops, and the sentence that follows names the number the
     * register holds the rest under, so an officer who needs the full text knows
     * exactly where to look rather than assuming the applicant wrote no more.
     */
    private static String printedReason(String reason) {
        String words = blank(reason).strip();
        if (words.length() <= REASON_ON_PAPER) {
            return words;
        }
        int cut = words.lastIndexOf(' ', REASON_ON_PAPER);
        if (cut <= 0) {
            cut = REASON_ON_PAPER;
        }
        return words.substring(0, cut).strip()
                + "... printed in full in the leave register under this number.";
    }

    /**
     * Every word on the page comes out of a staff record, a reason someone
     * typed or a name someone chose, so nothing reaches the output unescaped.
     * The rule lives in {@link Paper}, where every printed page shares it.
     */
    private static String esc(String text) {
        return Paper.esc(text);
    }

    /**
     * A4 sideways is 297 by 210 millimetres; five all round leaves 287 by 200
     * for the pair, which is two 135-millimetre forms and the cutting line.
     */
    private static String css() {
        return """
                @page { size: A4 landscape; margin: 5mm; }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: "Times New Roman", Times, serif; font-size: 10.5pt;
                       color: #000; print-color-adjust: exact; -webkit-print-color-adjust: exact; }
                .toolbar { display: flex; gap: 8px; align-items: center; padding: 10px 14px;
                           background: #f2f4f7; font-family: system-ui, sans-serif; font-size: 14px; }
                .toolbar button { padding: 6px 14px; }
                .toolbar .credit { margin-left: auto; color: #556070; font-size: 12px; }
                .sheet { display: flex; gap: 7mm; width: 287mm; height: 200mm;
                         margin: 0 auto 6mm; break-after: page; }
                .sheet:last-child { break-after: auto; }
                .form { width: 135mm; height: 190mm; border: 0.4mm solid #000; padding: 2.5mm 3mm;
                        display: flex; flex-direction: column; overflow: hidden; background: #fff; }
                .blank { width: 0; }
                .cut { width: 3mm; border-left: 0.4mm dashed #888; position: relative;
                       align-self: stretch; }
                .cut span { position: absolute; top: 50%; left: -6mm; transform: rotate(-90deg);
                            font-size: 7pt; color: #666; }
                .head { display: flex; gap: 2mm; align-items: flex-start; }
                .head .crest { width: 14mm; height: 14mm; object-fit: contain; }
                .head .title { flex: 1; text-align: center; }
                .head h1 { margin: 0; font-size: 12pt; }
                .head h2 { margin: 0.5mm 0 0; font-size: 10.5pt; }
                .head h3 { margin: 1.5mm 0 0; font-size: 10.5pt; text-decoration: underline; }
                .marks { width: 30mm; text-align: right; font-size: 8.5pt; }
                .marks .code { margin: 0; border: 0.3mm solid #000; padding: 0.6mm; font-weight: 600; }
                .marks .ref { margin: 0.8mm 0 0; font-weight: 600; }
                .marks .nokey { margin: 0.8mm 0 0; font-size: 7.5pt; font-style: italic; }
                .qr svg { display: block; margin-top: 1mm; }
                .check { margin: 0.4mm 0 0; font-size: 7pt; word-break: break-all; }
                table.fields { width: 100%; margin-top: 2mm; border-collapse: collapse; font-size: 10pt; }
                table.fields th { width: 32mm; text-align: left; font-weight: normal; vertical-align: top;
                                  padding: 1mm 0; }
                table.fields td { border-bottom: 0.25mm solid #000; padding: 1mm 1mm; }
                table.fields tr.tall td { height: 16mm; vertical-align: top; overflow-wrap: anywhere; }
                table.fields .triple { display: flex; gap: 3mm; }
                .note { margin: 2mm 0 0; font-size: 8.5pt; text-align: justify; }
                .sign { display: flex; justify-content: space-between; align-items: flex-end;
                        margin-top: auto; padding-top: 3mm; font-size: 9.5pt; }
                .sign > div > span { border-bottom: 0.3mm solid #000; padding: 0 12mm 0.6mm 0; }
                .sign .date { font-size: 9pt; }
                .office { margin-top: 3mm; border-top: 0.4mm solid #000; padding-top: 1.5mm; }
                .office h4 { margin: 0 0 1mm; font-size: 10pt; text-align: center;
                             text-decoration: underline; }
                .office p { margin: 0 0 1.2mm; font-size: 9.5pt; }
                .office .boxes { display: flex; gap: 6mm; }
                .office .box.on { font-weight: 700; }
                .yes { font-weight: 700; }
                .no { font-weight: 700; }
                .printed { margin: 1.5mm 0 0; font-size: 7.5pt; color: #444; }
                .comp { margin: 1mm 0 0; font-size: 8pt; border: 0.3mm solid #000; padding: 0.8mm;
                       text-align: center; }
                @media print { .no-print { display: none !important; } }
                """;
    }
}
