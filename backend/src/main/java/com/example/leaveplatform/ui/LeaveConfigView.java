package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import com.example.leaveplatform.admin.BalanceAdminService;
import com.example.leaveplatform.admin.BalanceSummary;
import com.example.leaveplatform.admin.EmployeeAdminService;
import com.example.leaveplatform.admin.EmployeeSummary;
import com.example.leaveplatform.admin.HolidayAdminService;
import com.example.leaveplatform.admin.HolidaySummary;
import com.example.leaveplatform.notification.NotificationService;

/**
 * The two pieces of leave configuration that the earlier admin panel kept and
 * this platform had no screen for: the public holiday calendar the day counter
 * reads, and a corrected allowance for one employee's year.
 *
 * <p>Deliberately not on the Administration screen. That screen already holds
 * faculties, departments, employees and accounts, and a fifth list there would
 * be harder to use than these two lists are here.
 */
@Route("leave-config")
@PageTitle("Leave configuration - University Leave Platform")
@RolesAllowed({"CENTRAL_ADMIN"})
public class LeaveConfigView extends VerticalLayout {

    private final HolidayAdminService holidays;
    private final BalanceAdminService balances;
    private final EmployeeAdminService employees;

    private final ComboBox<Integer> year = new ComboBox<>("Year");
    private final Grid<HolidaySummary> holidayGrid = new Grid<>(HolidaySummary.class, false);
    private final DatePicker holidayDate = new DatePicker("Date");
    private final TextField holidayName = new TextField("Holiday name");

    private final TextField staffSearch = new TextField("Staff number, name or email");
    private final Grid<EmployeeSummary> staffGrid = new Grid<>(EmployeeSummary.class, false);
    private final Paragraph staffNote = new Paragraph(
            "Only the first " + EmployeeAdminService.SEARCH_LIMIT + " matches are listed. Type part of a"
                    + " name or the full staff number to narrow it down.");
    private final Grid<BalanceSummary> balanceGrid = new Grid<>(BalanceSummary.class, false);
    private final Paragraph balanceNote = new Paragraph("Find the person first.");
    private EmployeeSummary selected;

    public LeaveConfigView(HolidayAdminService holidays, BalanceAdminService balances,
            EmployeeAdminService employees, AuthenticationContext authentication,
            NotificationService notifications) {
        this.holidays = holidays;
        this.balances = balances;
        this.employees = employees;

        VerticalLayout content = new VerticalLayout(
                new H2("Leave configuration"),
                holidaySection(),
                balanceSection(),
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refreshHolidays();
    }

    private VerticalLayout holidaySection() {
        List<Integer> years = new ArrayList<>();
        int current = LocalDate.now().getYear();
        for (int value = current - 3; value <= current + 2; value++) {
            years.add(value);
        }
        year.setItems(years);
        year.setValue(current);
        year.addValueChangeListener(event -> refreshHolidays());

        holidayGrid.addColumn(HolidaySummary::date).setHeader("Date").setAutoWidth(true);
        holidayGrid.addColumn(HolidaySummary::name).setHeader("Holiday").setWidth("420px").setFlexGrow(1);
        holidayGrid.addComponentColumn(this::removeButton).setHeader("Action").setAutoWidth(true);
        Grids.emptyText(holidayGrid, "No holiday is listed for this year yet.");
        Grids.scrollable(holidayGrid, "16rem");

        Button add = new Button("Add to the calendar", event -> Feedback.run("Holiday added.", () -> {
            holidays.add(holidayDate.getValue(), holidayName.getValue());
            holidayDate.clear();
            holidayName.clear();
            refreshHolidays();
        }));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        holidayName.setPlaceholder("for example Eid-ul-Fitr (2nd day)");
        HorizontalLayout addRow = Rows.wrap(holidayDate, holidayName, add);

        Paragraph note = new Paragraph("A day listed here is not counted as a leave day when a request"
                + " covers it. Changing the calendar does not re-count requests that were already made:"
                + " each request keeps the days the server counted when it was submitted.");
        note.addClassName("app-muted");

        return section("Public holiday calendar", year, note, addRow, holidayGrid);
    }

    private VerticalLayout balanceSection() {
        staffSearch.setWidthFull();
        staffSearch.setMaxWidth("280px");
        staffSearch.setPlaceholder("at least two characters");
        staffNote.addClassName("app-muted");
        staffNote.setVisible(false);
        staffSearch.addKeyPressListener(Key.ENTER, event -> refreshStaff());
        Button find = new Button("Find", event -> refreshStaff());
        staffGrid.addColumn(EmployeeSummary::employeeNo).setHeader("Staff no").setAutoWidth(true);
        staffGrid.addColumn(EmployeeSummary::fullName).setHeader("Name").setAutoWidth(true);
        staffGrid.addColumn(EmployeeSummary::departmentName).setHeader("Department").setAutoWidth(true);
        staffGrid.addComponentColumn(this::balancesButton).setHeader("Action").setAutoWidth(true);
        Grids.emptyText(staffGrid, "Nobody matches that search. Try a staff number or part of a name.");
        Grids.scrollable(staffGrid, "16rem");

        balanceGrid.addColumn(BalanceSummary::leaveTypeName).setHeader("Leave type").setAutoWidth(true);
        balanceGrid.addColumn(BalanceSummary::year).setHeader("Year").setAutoWidth(true);
        balanceGrid.addColumn(BalanceSummary::allocated).setHeader("Granted").setAutoWidth(true);
        balanceGrid.addColumn(BalanceSummary::used).setHeader("Used").setAutoWidth(true);
        balanceGrid.addColumn(BalanceSummary::remaining).setHeader("Remaining").setAutoWidth(true);
        balanceGrid.addComponentColumn(this::adjustButton).setHeader("Action").setAutoWidth(true);
        Grids.emptyText(balanceGrid, "This person has no balance row yet. One appears with the first"
                + " approval, or from a correction made here.");
        Grids.scrollable(balanceGrid, "14rem");

        return section("Balance corrections", Rows.wrap(staffSearch, find), staffNote,
                staffGrid, balanceNote, balanceGrid);
    }

    private Button removeButton(HolidaySummary holiday) {
        Button remove = new Button("Remove", event -> RemarkDialog.confirm(
                "Remove " + holiday.date() + " (" + holiday.name() + ") from the calendar?",
                "Requests already counted keep their days; only future requests change.",
                "Remove", "Holiday removed.", () -> {
                    holidays.remove(holiday.id());
                    refreshHolidays();
                }));
        remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        return remove;
    }

    private Button balancesButton(EmployeeSummary employee) {
        return new Button("Balances", event -> {
            selected = employee;
            balanceNote.setText("Balances of %s %s (%s)."
                    .formatted(employee.employeeNo(), employee.fullName(), employee.departmentName()));
            refreshBalances();
        });
    }

    private Button adjustButton(BalanceSummary balance) {
        Button adjust = new Button("Correct", event -> openCorrection(balance));
        adjust.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return adjust;
    }

    /**
     * The correction is opened from the row it changes, so the year, the type and
     * the days already used are on screen while the number is typed.
     */
    private void openCorrection(BalanceSummary balance) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Correct the %s allowance of %s for %d".formatted(
                balance.leaveTypeName(), balance.employeeName(), balance.year()));
        IntegerField granted = new IntegerField("Days granted for the year");
        granted.setMin(0);
        granted.setMax(365);
        granted.setValue(balance.allocated());
        granted.setHelperText("%d day(s) already used, so this cannot be lower than %d."
                .formatted(balance.used(), balance.used()));
        TextArea why = new TextArea("Reason");
        why.setRequiredIndicatorVisible(true);
        why.setWidthFull();
        why.setPlaceholder("for example HR order 12/2026: transferred in with 3 days already spent");
        dialog.add(granted, why);

        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Save correction", event -> {
            Integer value = granted.getValue();
            if (value == null) {
                Feedback.error("Type the number of days granted.");
                return;
            }
            String reason = why.getValue();
            dialog.close();
            Feedback.run("Balance corrected.", () -> {
                balances.adjust(selected.id(), balance.leaveTypeCode(), balance.year(), value, reason);
                refreshBalances();
            });
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void refreshHolidays() {
        try {
            holidayGrid.setItems(holidays.list(year.getValue()));
        } catch (RuntimeException exception) {
            Feedback.error("Could not load the holiday calendar.");
        }
    }

    private void refreshStaff() {
        try {
            List<EmployeeSummary> found = employees.search(staffSearch.getValue(), null);
            staffGrid.setItems(found);
            staffNote.setVisible(found.size() >= EmployeeAdminService.SEARCH_LIMIT);
        } catch (RuntimeException exception) {
            Feedback.error("Could not search the staff records.");
        }
    }

    private void refreshBalances() {
        if (selected == null) {
            return;
        }
        try {
            balanceGrid.setItems(balances.forEmployee(selected.id()));
        } catch (RuntimeException exception) {
            Feedback.error("Could not load the balances.");
        }
    }

    private static VerticalLayout section(String title, Component... parts) {
        Component[] children = Stream.concat(Stream.of(new H3(title)), Stream.of(parts))
                .toArray(Component[]::new);
        VerticalLayout card = new VerticalLayout(children);
        card.addClassName("app-card");
        card.setPadding(false);
        card.setWidthFull();
        return card;
    }
}
