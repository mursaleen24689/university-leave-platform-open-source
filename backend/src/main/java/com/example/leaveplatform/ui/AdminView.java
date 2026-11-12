package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.admin.AccountAdminService;
import com.example.leaveplatform.admin.AdminException;
import com.example.leaveplatform.admin.DepartmentSummary;
import com.example.leaveplatform.admin.EmployeeAdminService;
import com.example.leaveplatform.admin.EmployeeInput;
import com.example.leaveplatform.admin.EmployeeSummary;
import com.example.leaveplatform.admin.FacultySummary;
import com.example.leaveplatform.admin.ImportResult;
import com.example.leaveplatform.admin.OrganizationAdminService;
import com.example.leaveplatform.mail.MailService;
import com.example.leaveplatform.mail.MailSettingsService;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.organization.DepartmentType;
import com.example.leaveplatform.profile.ProfileRequestService;
import com.example.leaveplatform.support.TicketService;

/**
 * Data entry for the organisation and staff. The university cannot publish a
 * full staff list, so records are created here and completed as HR supplies
 * details: only name, designation and department are required.
 */
@Route("admin")
@PageTitle("Administration - University Leave Platform")
@RolesAllowed("CENTRAL_ADMIN")
public class AdminView extends VerticalLayout {

    private final OrganizationAdminService organization;
    private final EmployeeAdminService employees;

    private final TextField facultyCode = new TextField("Code");
    private final TextField facultyName = new TextField("Faculty name");
    private final Grid<FacultySummary> facultyGrid = new Grid<>(FacultySummary.class, false);

    private final ComboBox<FacultySummary> departmentFaculty = new ComboBox<>("Faculty");
    private final TextField departmentCode = new TextField("Code");
    private final TextField departmentName = new TextField("Department or office name");
    private final ComboBox<DepartmentType> departmentType = new ComboBox<>("Type");
    private final Grid<DepartmentSummary> departmentGrid = new Grid<>(DepartmentSummary.class, false);

    private final TextField employeeSearch = new TextField("Search name or staff number");
    private final ComboBox<DepartmentSummary> employeeFilter = new ComboBox<>("Department");
    private final Grid<EmployeeSummary> employeeGrid = new Grid<>(EmployeeSummary.class, false);
    private final Paragraph employeeNote = new Paragraph(
            "Only the first " + EmployeeAdminService.SEARCH_LIMIT + " matches are listed. Type part of a"
                    + " name or choose a department to narrow it down.");

    private final TextField employeeNo = new TextField("Staff number");
    private final TextField fullName = new TextField("Full name");
    private final TextField designation = new TextField("Designation");
    private final ComboBox<DepartmentSummary> department = new ComboBox<>("Department");
    private final TextField supervisorNo = new TextField("Supervisor staff number");
    private final TextField email = new TextField("E-mail");
    private final TextField phone = new TextField("Phone");
    private Long editingEmployeeId;

    private final TextArea importText = new TextArea("CSV rows");
    private final Paragraph importResult = new Paragraph();
    private final VerticalLayout content = new VerticalLayout();

    public AdminView(OrganizationAdminService organization, EmployeeAdminService employees,
            AccountAdminService accounts, MailSettingsService mailSettings, MailService mail,
            TicketService tickets, ProfileRequestService requests,
            AuthenticationContext authentication, NotificationService notifications) {
        this.organization = organization;
        this.employees = employees;

        String actor = authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("unknown");

        content.add(new H2("Administration"));
        addFaculties();
        addDepartments();
        addEmployees();
        addImport();
        content.add(new AccountsSection(accounts, actor), new MailSection(mailSettings, mail, actor),
                new TicketsSection(tickets, actor), new ProfileSection(requests, actor),
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refreshOrganization();
        refreshEmployees();
    }

    private void addFaculties() {
        facultyGrid.addColumn(FacultySummary::code).setHeader("Code").setAutoWidth(true);
        facultyGrid.addColumn(FacultySummary::name).setHeader("Name").setAutoWidth(true);
        facultyGrid.addColumn(faculty -> state(faculty.active())).setHeader("Status").setAutoWidth(true);
        Grids.scrollable(facultyGrid, "14rem");
        Grids.emptyText(facultyGrid, "No faculty is recorded yet. Add one before the departments.");

        Button add = new Button("Add faculty", event -> Feedback.run("Faculty added.", this::createFaculty));

        section("Faculties", facultyGrid, Rows.wrap(facultyCode, facultyName, add));
    }

    /** Wraps one block of the screen in a card so the page reads as sections. */
    private void section(String title, Component... parts) {
        VerticalLayout card = new VerticalLayout(new H3(title));
        card.add(parts);
        card.addClassName("app-card");
        card.setPadding(false);
        card.setWidthFull();
        content.add(card);
    }

    /** ACADEMIC is the value in the database; on a screen it reads as Academic. */
    private static String typeLabel(DepartmentType type) {
        return type == null ? ""
                : type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private void addDepartments() {
        departmentFaculty.setItemLabelGenerator(FacultySummary::name);
        departmentType.setItemLabelGenerator(AdminView::typeLabel);
        departmentType.setItems(DepartmentType.values());

        departmentGrid.addColumn(DepartmentSummary::code).setHeader("Code").setAutoWidth(true);
        departmentGrid.addColumn(DepartmentSummary::name).setHeader("Name").setAutoWidth(true);
        departmentGrid.addColumn(DepartmentSummary::facultyCode).setHeader("Faculty").setAutoWidth(true);
        departmentGrid.addColumn(department -> typeLabel(department.type())).setHeader("Type").setAutoWidth(true);
        departmentGrid.addColumn(department -> state(department.active()))
                .setHeader("Status").setAutoWidth(true);
        departmentGrid.addComponentColumn(summary -> new Button(
                summary.active() ? "Deactivate" : "Activate",
                event -> Feedback.run(summary.active() ? "Department deactivated."
                        : "Department activated.", () -> {
                    organization.setDepartmentActive(summary.id(), !summary.active());
                    refreshOrganization();
                }))).setHeader("Action");
        Grids.scrollable(departmentGrid);
        Grids.emptyText(departmentGrid, "No department or office yet. Add the first one above.");

        Button add = new Button("Add department",
                event -> Feedback.run("Department added.", this::createDepartment));

        HorizontalLayout form = Rows.wrap(departmentFaculty, departmentCode, departmentName,
                departmentType, add);

        section("Departments and offices", departmentGrid, form);
    }

    private void addEmployees() {
        employeeSearch.setClearButtonVisible(true);
        employeeSearch.addValueChangeListener(event -> refreshEmployees());
        employeeFilter.setItemLabelGenerator(DepartmentSummary::name);
        employeeFilter.setClearButtonVisible(true);
        employeeFilter.addValueChangeListener(event -> refreshEmployees());
        department.setItemLabelGenerator(DepartmentSummary::name);
        employeeNote.addClassName("app-muted");
        employeeNote.setVisible(false);

        employeeGrid.addColumn(EmployeeSummary::employeeNo).setHeader("Staff no").setAutoWidth(true);
        employeeGrid.addColumn(EmployeeSummary::fullName).setHeader("Name").setAutoWidth(true);
        employeeGrid.addColumn(EmployeeSummary::designation).setHeader("Designation").setAutoWidth(true);
        employeeGrid.addColumn(EmployeeSummary::departmentCode).setHeader("Department").setAutoWidth(true);
        employeeGrid.addColumn(EmployeeSummary::email).setHeader("E-mail").setAutoWidth(true);
        employeeGrid.addColumn(EmployeeSummary::phone).setHeader("Phone").setAutoWidth(true);
        employeeGrid.addColumn(employee -> state(employee.active()))
                .setHeader("Status").setAutoWidth(true);
        employeeGrid.addComponentColumn(summary -> Rows.wrap(
                new Button("Edit", event -> edit(summary)),
                new Button(summary.active() ? "Deactivate" : "Activate",
                        event -> Feedback.run(summary.active() ? "Employee deactivated."
                                : "Employee activated.", () -> {
                            employees.setActive(summary.id(), !summary.active());
                            refreshEmployees();
                        })))).setHeader("Action");
        Grids.scrollable(employeeGrid);
        Grids.emptyText(employeeGrid, "Nobody matches this search or department. Clear the search, "
                + "or add the person with the form below.");

        employeeNo.setHelperText("Optional until HR supplies it.");
        email.setHelperText("Optional; can be added later.");
        phone.setHelperText("Optional; can be added later.");

        Button save = new Button("Save employee", event -> Feedback.run(this::saveEmployee));
        Button clear = new Button("New", event -> clearEmployeeForm());

        VerticalLayout details = new VerticalLayout(
                Rows.wrap(employeeNo, fullName, designation),
                Rows.wrap(department, supervisorNo),
                Rows.wrap(email, phone),
                Rows.wrap(save, clear));
        details.setPadding(false);
        details.setSpacing(false);

        section("Employees", Rows.wrap(employeeSearch, employeeFilter),
                employeeNote, employeeGrid, details);
    }

    private void addImport() {
        importText.setWidthFull();
        importText.setHeight("160px");
        importText.setPlaceholder(EmployeeAdminService.IMPORT_HEADER + "\n7021,Ayesha Khan,Assistant Professor,CS,ayesha@example.edu,+92 300 0000000,");
        importResult.getStyle().set("white-space", "pre-wrap");

        Button run = new Button("Import rows",
                event -> Feedback.run("Import finished.", this::importEmployees));

        section("Bulk import", new Paragraph(
                "Paste rows from a spreadsheet. The header is optional. Existing staff numbers are updated; "
                        + "rows without a staff number, or with an unknown department, are reported and skipped."),
                importText, run, importResult);
    }

    private void createFaculty() {
        organization.createFaculty(facultyCode.getValue(), facultyName.getValue());
        facultyCode.clear();
        facultyName.clear();
        refreshOrganization();
    }

    private void createDepartment() {
        organization.createDepartment(
                departmentFaculty.getValue() == null ? null : departmentFaculty.getValue().id(),
                departmentCode.getValue(), departmentName.getValue(),
                departmentType.getValue() == null ? null : departmentType.getValue().name());
        departmentCode.clear();
        departmentName.clear();
        refreshOrganization();
    }

    private void saveEmployee() {
        EmployeeInput input = new EmployeeInput(
                employeeNo.getValue(), fullName.getValue(), designation.getValue(),
                department.getValue() == null ? null : department.getValue().id(),
                resolveSupervisor(),
                email.getValue(), phone.getValue());
        if (editingEmployeeId == null) {
            employees.create(input);
            Feedback.success("Employee added.");
        } else {
            employees.update(editingEmployeeId, input);
            Feedback.success("Employee updated.");
        }
        clearEmployeeForm();
        refreshEmployees();
    }

    private Long resolveSupervisor() {
        String staffNo = supervisorNo.getValue();
        if (staffNo == null || staffNo.isBlank()) {
            return null;
        }
        return employees.search(staffNo, null).stream()
                .filter(found -> staffNo.equalsIgnoreCase(found.employeeNo()))
                .map(EmployeeSummary::id)
                .findFirst()
                .orElseThrow(() -> new AdminException("Supervisor staff number " + staffNo + " is not known."));
    }

    private void edit(EmployeeSummary summary) {
        editingEmployeeId = summary.id();
        employeeNo.setValue(summary.employeeNo() == null ? "" : summary.employeeNo());
        fullName.setValue(summary.fullName());
        designation.setValue(summary.designation());
        email.setValue(summary.email() == null ? "" : summary.email());
        phone.setValue(summary.phone() == null ? "" : summary.phone());
        department.setValue(departmentItems().stream()
                .filter(item -> item.id().equals(summary.departmentId()))
                .findFirst().orElse(null));
        supervisorNo.setValue("");
    }

    private void clearEmployeeForm() {
        editingEmployeeId = null;
        employeeNo.clear();
        fullName.clear();
        designation.clear();
        email.clear();
        phone.clear();
        supervisorNo.clear();
        department.clear();
    }

    private void importEmployees() {
        ImportResult result = employees.importCsv(importText.getValue());
        StringBuilder text = new StringBuilder("%d created, %d updated, %d skipped."
                .formatted(result.created(), result.updated(), result.failed()));
        for (String message : result.messages()) {
            text.append(System.lineSeparator()).append(message);
        }
        importResult.setText(text.toString());
        refreshEmployees();
    }

    private void refreshOrganization() {
        List<FacultySummary> faculty = organization.listFaculties();
        List<DepartmentSummary> departments = organization.listDepartments();
        facultyGrid.setItems(faculty);
        departmentGrid.setItems(departments);
        departmentFaculty.setItems(faculty.stream().filter(FacultySummary::active).toList());
        employeeFilter.setItems(departments);
        department.setItems(departments.stream().filter(DepartmentSummary::active).toList());
    }

    private List<DepartmentSummary> departmentItems() {
        return organization.listDepartments().stream().filter(DepartmentSummary::active).toList();
    }

    private void refreshEmployees() {
        List<EmployeeSummary> found = employees.search(
                employeeSearch.getValue(),
                employeeFilter.getValue() == null ? null : employeeFilter.getValue().id());
        employeeGrid.setItems(found);
        employeeNote.setVisible(found.size() >= EmployeeAdminService.SEARCH_LIMIT);
    }

    /**
     * A list that says "true" or "false" in a status column makes the reader
     * work out which way round it reads.
     */
    private static String state(boolean active) {
        return active ? "Active" : "Inactive";
    }
}
