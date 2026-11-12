package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveRuleException;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.organization.DepartmentResponse;
import com.example.leaveplatform.organization.DepartmentService;
import com.example.leaveplatform.reporting.AttendanceRow;
import com.example.leaveplatform.reporting.AttendanceSheet;
import com.example.leaveplatform.reporting.LeaveRegisterRow;
import com.example.leaveplatform.reporting.ReportService;
import com.example.leaveplatform.reporting.StaffSummaryRow;

/**
 * The three reports a department works from, on one screen: the leave register,
 * the staff summary and the monthly attendance sheet. Each has the same period
 * questions the lists use, and the download sits under the list it belongs to.
 *
 * <p>Who may open it, and which rows come back, is decided by
 * {@link ReportService} - this screen only asks. The download goes to the same
 * endpoint an API client uses, so the file and the list above it can never be
 * two different questions.
 *
 * <p>The sheet marks only what the platform holds: approved leave, a request
 * waiting for a decision, a weekend and a holiday. An empty day is left empty
 * rather than read as present, because nothing here records attendance.
 */
@Route("reports")
@PageTitle("Reports - University Leave Platform")
@RolesAllowed({AppRole.CENTRAL_ADMIN, AppRole.HOD})
public class ReportsView extends VerticalLayout {

    private final ReportService reports;
    private final AuthenticationContext authentication;

    /** Which month the sheet columns were last built for. */
    private String sheetColumns = "";

    private final Paragraph scopeLine = new Paragraph();
    private final ComboBox<DepartmentResponse> departmentChoice = new ComboBox<>("Department");
    private final FilterRow registerFilters = new FilterRow(true, "Filter", this::refresh);
    private final Paragraph registerNote = new Paragraph();
    private final Grid<LeaveRegisterRow> registerGrid = new Grid<>(LeaveRegisterRow.class, false);
    private final Anchor registerDownload = newAnchor("Download the register as a spreadsheet file");
    private final Button printForms =
            new Button("Print the application forms for the rows ticked below");

    private final IntegerField year = new IntegerField("Year");
    private final Paragraph summaryNote = new Paragraph();
    private final Grid<StaffSummaryRow> summaryGrid = new Grid<>(StaffSummaryRow.class, false);
    private final Anchor summaryDownload = newAnchor("Download the summary as a spreadsheet file");
    private final Button printRecords =
            new Button("Print the leave records for the names ticked below");

    private final ComboBox<Integer> monthChoice = new ComboBox<>("Month");
    private final Paragraph attendanceNote = new Paragraph();
    private final Paragraph attendanceLegend = new Paragraph();
    private final Grid<AttendanceRow> attendanceGrid = new Grid<>(AttendanceRow.class, false);
    private final Anchor attendanceDownload =
            newAnchor("Download the attendance sheet as a spreadsheet file");

    public ReportsView(ReportService reports, DepartmentService departments,
            AuthenticationContext authentication, NotificationService notifications) {
        this.reports = reports;
        this.authentication = authentication;

        departmentChoice.setItemLabelGenerator(DepartmentResponse::name);
        departmentChoice.setClearButtonVisible(true);
        departmentChoice.setHelperText("Nothing chosen: both reports cover the whole university.");
        departmentChoice.addValueChangeListener(event -> refresh());
        // An HOD has nothing to choose, so the box and the list behind it stay away.
        if (authentication.getGrantedRoles().contains(AppRole.CENTRAL_ADMIN)) {
            departmentChoice.setItems(departments.listActive(null));
        }

        year.setMin(1900);
        year.setMax(9999);
        year.setValue(LocalDate.now().getYear());
        year.setHelperText("The summary and the attendance sheet are built for this year.");
        year.addValueChangeListener(event -> refresh());

        monthChoice.setItemLabelGenerator(ReportsView::monthName);
        monthChoice.setItems(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        monthChoice.setValue(LocalDate.now().getMonthValue());
        monthChoice.addValueChangeListener(event -> refresh());

        // The forms are the one report that leaves the platform as paper, so the
        // button states which rows it is taking: a person who printed the pile
        // they were looking at and a person who printed everything are two
        // different things to have done.
        printForms.addClickListener(event -> printChosen());
        printRecords.addClickListener(event -> printChosenRecords());

        scopeLine.addClassName("app-muted");
        registerNote.addClassName("app-empty");
        summaryNote.addClassName("app-empty");
        attendanceNote.addClassName("app-empty");
        attendanceLegend.addClassName("app-muted");

        registerGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        registerGrid.addColumn(LeaveRegisterRow::reference).setHeader("Reference").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::employeeNo).setHeader("Staff No").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::employeeName).setHeader("Name").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::department).setHeader("Department").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::leaveType).setHeader("Type").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::startDate).setHeader("From").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::endDate).setHeader("To").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::days).setHeader("Days").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::statusLabel).setHeader("Status").setAutoWidth(true);
        registerGrid.addColumn(row -> row.compensatory() ? "Yes" : "").setHeader("Compensatory")
                .setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::reason).setHeader("Reason").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::remarks).setHeader("Remarks").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::appliedOn).setHeader("Applied On").setAutoWidth(true);
        registerGrid.addColumn(LeaveRegisterRow::decidedOn).setHeader("Decided On").setAutoWidth(true);
        Grids.scrollable(registerGrid);

        summaryGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        summaryGrid.addColumn(StaffSummaryRow::employeeNo).setHeader("Staff No").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::employeeName).setHeader("Name").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::department).setHeader("Department").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::designation).setHeader("Designation").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::allocated).setHeader("Granted").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::used).setHeader("Used").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::remaining).setHeader("Left").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::approvedDays).setHeader("Approved Days").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::waitingDays).setHeader("Waiting Days").setAutoWidth(true);
        summaryGrid.addColumn(StaffSummaryRow::requests).setHeader("Requests").setAutoWidth(true);
        Grids.scrollable(summaryGrid);

        VerticalLayout content = new VerticalLayout(
                new H2("Reports"), scopeLine, Rows.wrap(departmentChoice),
                new H3("Leave register"), registerFilters, registerNote, registerGrid,
                Rows.wrap(registerDownload), Rows.wrap(printForms),
                new H3("Staff summary"), Rows.wrap(year), summaryNote, summaryGrid,
                Rows.wrap(summaryDownload), Rows.wrap(printRecords),
                new H3("Monthly attendance sheet"), Rows.wrap(monthChoice), attendanceNote,
                attendanceLegend, attendanceGrid, Rows.wrap(attendanceDownload),
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private void refresh() {
        try {
            ReportService.ReportScope scope = reports.scopeFor(username());
            departmentChoice.setVisible(scope.wholeUniversity());
            scopeLine.setText(scope.wholeUniversity()
                    ? "A central administrator's view: choose one department, or leave the box empty "
                            + "for the whole university."
                    : "Your department: " + scope.departmentName() + ". These reports cover it and "
                            + "nothing else.");

            Long chosen = chosen(scope);
            // Say which shelf was searched, or an administrator who picked an
            // empty department reads "nothing recorded" as the whole university.
            String where = !scope.wholeUniversity() ? " in your department"
                    : departmentChoice.getValue() == null ? ""
                            : " in " + departmentChoice.getValue().name();
            LeaveFilter filter = registerFilters.value();
            List<LeaveRegisterRow> found = reports.register(username(), chosen, filter);
            registerGrid.setItems(found);
            registerNote.setText(ListNote.ofRegister(found.size(), filter));
            registerNote.setVisible(!found.isEmpty());
            Grids.emptyText(registerGrid, filter.isAny()
                    ? "No leave request has been recorded yet" + where + "."
                    : "Nothing matches %s. Widen the dates or choose another status."
                            .formatted(filter.describe()));
            registerDownload.setHref(registerHref(chosen, filter));

            int yearValue = year.getValue() == null ? LocalDate.now().getYear() : year.getValue();
            List<StaffSummaryRow> staff = reports.staffSummary(username(), chosen, yearValue);
            summaryGrid.setItems(staff);
            summaryNote.setText(ListNote.ofSummary(staff.size(), yearValue));
            summaryNote.setVisible(!staff.isEmpty());
            Grids.emptyText(summaryGrid, "No active employee is recorded" + where + ".");
            summaryDownload.setHref(summaryHref(chosen, yearValue));

            int monthValue = monthChoice.getValue() == null
                    ? LocalDate.now().getMonthValue() : monthChoice.getValue();
            AttendanceSheet sheet =
                    reports.attendanceSheet(username(), chosen, yearValue, monthValue);
            buildSheetColumns(sheet);
            attendanceGrid.setItems(sheet.rows());
            attendanceNote.setText(ListNote.ofSheet(sheet.rows().size(), sheet));
            attendanceNote.setVisible(!sheet.rows().isEmpty());
            attendanceLegend.setText(sheet.legend());
            Grids.emptyText(attendanceGrid, "No active employee is recorded" + where + ".");
            attendanceDownload.setHref(attendanceHref(chosen, yearValue, monthValue));
        } catch (ResponseStatusException | LeaveRuleException exception) {
            Feedback.error(exception);
        }
    }

    /**
     * The day columns belong to the month, so they are rebuilt when the month
     * changes and left alone otherwise: a person who has scrolled the sheet does
     * not lose their place every time the screen refreshes.
     */
    private void buildSheetColumns(AttendanceSheet sheet) {
        String monthKey = sheet.year() + "-" + sheet.month();
        if (monthKey.equals(sheetColumns)) {
            return;
        }
        sheetColumns = monthKey;
        attendanceGrid.removeAllColumns();
        attendanceGrid.addColumn(AttendanceRow::employeeNo).setHeader("Staff No").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::employeeName).setHeader("Name").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::department).setHeader("Department").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::designation).setHeader("Designation").setAutoWidth(true);
        for (int day = 1; day <= sheet.days(); day++) {
            int mark = day - 1;
            attendanceGrid.addColumn(row -> row.marks().get(mark)).setHeader(String.valueOf(day))
                    .setAutoWidth(true);
        }
        attendanceGrid.addColumn(AttendanceRow::approvedDays).setHeader("Approved Days").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::waitingDays).setHeader("Waiting Days").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::allocated).setHeader("Granted").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::used).setHeader("Used").setAutoWidth(true);
        attendanceGrid.addColumn(AttendanceRow::remaining).setHeader("Left").setAutoWidth(true);
        Grids.scrollable(attendanceGrid);
    }

    /**
     * Opens the chosen forms in a tab of its own. Paper is a document rather
     * than another state of this screen, so a person who prints a pile and then
     * carries on filtering does not lose the list they came from, and the
     * browser's own print dialogue does the measuring.
     */
    private void printChosen() {
        List<LeaveRegisterRow> picked = List.copyOf(registerGrid.getSelectedItems());
        if (picked.isEmpty()) {
            Feedback.error("Tick the requests whose forms you want, then press this again.");
            return;
        }
        if (picked.size() > ReportService.PRINT_LIMIT) {
            Feedback.error("One print run carries at most " + ReportService.PRINT_LIMIT
                    + " forms. Print another pile for the rest.");
            return;
        }
        List<Long> ids = picked.stream().map(LeaveRegisterRow::id).sorted().toList();
        openInNewTab(isoFormHref(ids));
    }

    /**
     * Opens the chosen officers' records, one sheet each, for the year this list
     * was built for. The paper rules are the application form's: the names
     * ticked are the pages printed, the year travels with them so the sheet
     * says which period it speaks of, and a pile too big for one run is said so
     * here rather than cut short by the server.
     */
    private void printChosenRecords() {
        List<StaffSummaryRow> picked = List.copyOf(summaryGrid.getSelectedItems());
        if (picked.isEmpty()) {
            Feedback.error("Tick the names whose records you want, then press this again.");
            return;
        }
        if (picked.size() > ReportService.PRINT_LIMIT) {
            Feedback.error("One print run carries at most " + ReportService.PRINT_LIMIT
                    + " records. Print another pile for the rest.");
            return;
        }
        int yearValue = year.getValue() == null ? LocalDate.now().getYear() : year.getValue();
        List<Long> ids = picked.stream().map(StaffSummaryRow::id).sorted().toList();
        openInNewTab(recordHref(ids, yearValue));
    }

    /**
     * Asks the browser to open the printed paper in a tab of its own.
     *
     * <p>{@code Page.open} rather than a hand-written {@code window.open}
     * because the address has to reach the browser whole. A script that takes it
     * from {@code arguments} opens the address "undefined" and prints nothing at
     * all, and the screen still looks as though it worked.
     */
    private void openInNewTab(String address) {
        getUI().ifPresent(ui -> ui.getPage().open(address, "_blank"));
    }

    /** An HOD never chooses: the service fixes them to their own department. */
    private Long chosen(ReportService.ReportScope scope) {
        if (!scope.wholeUniversity()) {
            return null;
        }
        DepartmentResponse selected = departmentChoice.getValue();
        return selected == null ? null : selected.id();
    }

    /**
     * The address of the register file, carrying the questions the person just
     * answered. Every value is a number or an ISO date, so nothing needs
     * escaping - and a department named here is still refused by the service if
     * it is not this person's to report on.
     */
    static String registerHref(Long departmentId, LeaveFilter filter) {
        List<String> parts = params(departmentId);
        if (filter.from() != null) {
            parts.add("from=" + filter.from());
        }
        if (filter.to() != null) {
            parts.add("to=" + filter.to());
        }
        if (filter.status() != null) {
            parts.add("status=" + filter.status().name());
        }
        return "/api/v1/reports/register/csv?" + String.join("&", parts);
    }

    /** The address of the staff summary file. An HOD never has a department in it. */
    static String summaryHref(Long departmentId, int yearValue) {
        List<String> parts = params(departmentId);
        parts.add("year=" + yearValue);
        return "/api/v1/reports/staff-summary/csv?" + String.join("&", parts);
    }

    /** The address of the attendance file for one month. */
    static String attendanceHref(Long departmentId, int yearValue, int month) {
        List<String> parts = params(departmentId);
        parts.add("year=" + yearValue);
        parts.add("month=" + month);
        return "/api/v1/reports/attendance/csv?" + String.join("&", parts);
    }

    /**
     * The address of a print run. The ids travel as they are listed on the
     * screen; which of them this caller may print is the service's question, and
     * a request from another department is refused there rather than quietly
     * left out of the pile.
     */
    static String isoFormHref(List<Long> ids) {
        return "/api/v1/reports/iso-form?ids=" + ids.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * The address of a record run. The ids are employee ids from the summary
     * list and the year is the one that list was built for, so the sheet in the
     * new tab answers the same question as the screen it came from.
     */
    static String recordHref(List<Long> ids, int yearValue) {
        return "/api/v1/reports/employee-record?year=" + yearValue + "&ids="
                + ids.stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));
    }

    /** November, not 11: a person choosing a month reads a name. */
    static String monthName(Integer month) {
        return month == null ? ""
                : Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static List<String> params(Long departmentId) {
        List<String> parts = new ArrayList<>();
        if (departmentId != null) {
            parts.add("departmentId=" + departmentId);
        }
        return parts;
    }

    /** A plain link that asks for the file; the browser keeps it on disk. */
    private static Anchor newAnchor(String caption) {
        Anchor download = new Anchor("#", caption);
        download.getElement().setAttribute("download", true);
        return download;
    }

    private String username() {
        return authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
    }
}
