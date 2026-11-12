package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.example.leaveplatform.admin.AccountAdminService;
import com.example.leaveplatform.admin.AccountCandidate;
import com.example.leaveplatform.admin.AccountInput;
import com.example.leaveplatform.admin.AccountSummary;
import com.example.leaveplatform.admin.IssuedCredentials;

/**
 * Sign-in accounts for the staff records entered on the administration screen.
 * A new account receives a temporary password that is shown once; the person
 * must choose their own password at the first sign-in.
 */
class AccountsSection extends VerticalLayout {

    private static final DateTimeFormatter LAST_SEEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final AccountAdminService accounts;
    private final String actor;

    private final TextField search = new TextField("Search username or name");
    private final Grid<AccountSummary> grid = new Grid<>(AccountSummary.class, false);

    private final ComboBox<AccountCandidate> candidate = new ComboBox<>("Employee");
    private final TextField username = new TextField("Username");
    private final TextField temporaryPassword = new TextField("Temporary password (optional)");
    private final CheckboxGroup<String> newRoles = new CheckboxGroup<>("Additional roles");
    private final Button create = new Button("Create account");

    AccountsSection(AccountAdminService accounts, String actor) {
        this.accounts = accounts;
        this.actor = actor;

        add(new H3("Sign-in accounts"), description(), search, grid, createForm());
        addClassName("app-card");
        setWidthFull();
        setPadding(false);
        setSpacing(true);

        configureSearch();
        configureGrid();
        configureCreateForm();
        refresh();
    }

    private static Paragraph description() {
        return new Paragraph("Each employee needs an account before they can sign in. "
                + "The temporary password is shown only once; the person chooses a new one at first sign-in.");
    }

    private void configureSearch() {
        search.setClearButtonVisible(true);
        search.setWidthFull();
        search.setMaxWidth("320px");
        search.addValueChangeListener(event -> refresh());
    }

    private void configureGrid() {
        grid.addColumn(AccountSummary::username).setHeader("Username").setAutoWidth(true);
        grid.addColumn(AccountSummary::employeeName).setHeader("Employee").setAutoWidth(true);
        grid.addColumn(AccountSummary::employeeNo).setHeader("Staff no").setAutoWidth(true);
        grid.addColumn(AccountSummary::departmentCode).setHeader("Department").setAutoWidth(true);
        grid.addColumn(summary -> RoleLabels.list(summary.roles())).setHeader("Roles").setAutoWidth(true);
        grid.addColumn(summary -> summary.enabled() ? "Yes" : "No").setHeader("Enabled").setAutoWidth(true);
        grid.addColumn(summary -> summary.mustChangePassword() ? "Pending" : "Done")
                .setHeader("Password change").setAutoWidth(true);
        grid.addColumn(summary -> summary.lastLoginAt() == null
                ? "Never" : LAST_SEEN.format(summary.lastLoginAt()))
                .setHeader("Last sign-in").setAutoWidth(true);
        grid.addColumn(summary -> summary.twoStepEnabled()
                ? "On since " + LAST_SEEN.format(summary.twoStepSince()) : "Off")
                .setHeader("Two-step").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader("Action");
        Grids.scrollable(grid);
        Grids.emptyText(grid, "No account matches that search. Type part of a username or a name, "
                + "or create the first account with the form below.");
    }

    private HorizontalLayout actions(AccountSummary summary) {
        Button password = new Button("Password",
                event -> Feedback.run("A new temporary password is shown once. Copy it now.",
                        () -> resetPassword(summary)));
        Button roles = new Button("Roles", event -> editRoles(summary));
        Button toggle = new Button(summary.enabled() ? "Disable" : "Enable",
                event -> Feedback.run(summary.enabled() ? "Account disabled." : "Account enabled.", () -> {
                    accounts.setEnabled(summary.id(), !summary.enabled(), actor);
                    refresh();
                }));
        if (!summary.enabled()) {
            toggle.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        }
        List<Button> buttons = new ArrayList<>(List.of(password, roles, toggle));
        if (summary.twoStepEnabled()) {
            // The way back for somebody who cannot produce a code any more. It
            // is deliberately one-way: turning two-step on belongs to the
            // person holding the phone, so no screen offers it for anybody else.
            buttons.add(new Button("Two-step off", event -> Feedback.run(
                    "Two-step signing is off for that account. They can sign in with their password"
                            + " alone until they set it up again.",
                    () -> {
                        accounts.clearTwoStep(summary.id(), actor);
                        refresh();
                    })));
        }
        return Rows.wrap(buttons.toArray(new Button[0]));
    }

    private VerticalLayout createForm() {
        candidate.setItemLabelGenerator(AccountCandidate::label);
        candidate.setPlaceholder("Only employees without an account are listed");
        candidate.setWidthFull();
        candidate.setMaxWidth("320px");
        username.setPlaceholder("firstname.lastname");
        username.setHelperText("Lower-case letters, digits, dots, dashes or underscores.");
        temporaryPassword.setHelperText("Leave blank to generate a strong password.");
        newRoles.setHelperText("Leave all of these unticked for a person who only applies for their"
                + " own leave. Every account can do that.");

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout fields = Rows.wrap(candidate, username, temporaryPassword);

        VerticalLayout form = new VerticalLayout(fields, newRoles, create);
        form.setPadding(false);
        form.setSpacing(false);
        return form;
    }

    /**
     * Employee is granted by the service on every account, so offering it as a
     * tick would only let an administrator untick something that stays on.
     */
    private static List<String> extraRoles() {
        return AccountAdminService.ASSIGNABLE_ROLES.stream()
                .filter(role -> !"EMPLOYEE".equals(role))
                .toList();
    }

    private void configureCreateForm() {
        newRoles.setItemLabelGenerator(RoleLabels::plain);
        newRoles.setItems(extraRoles());

        candidate.addValueChangeListener(event -> {
            AccountCandidate chosen = event.getValue();
            if (chosen != null && (username.getValue() == null || username.getValue().isBlank())) {
                username.setValue(suggest(chosen.fullName()));
            }
        });

        create.addClickListener(event -> Feedback.run(this::createAccount));
    }

    /** Turns a full name into a likely username; the administrator may edit it. */
    static String suggest(String fullName) {
        if (fullName == null) {
            return "";
        }
        String value = fullName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("[^a-z0-9\\s._-]", "")
                .replaceAll("[\\s_]+", ".")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^\\.|\\.$", "");
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private void createAccount() {
        AccountCandidate chosen = candidate.getValue();
        Set<String> roles = new LinkedHashSet<>(newRoles.getValue());
        IssuedCredentials issued = accounts.create(new AccountInput(
                chosen == null ? null : chosen.employeeId(),
                username.getValue(),
                temporaryPassword.getValue(),
                new ArrayList<>(roles)), actor);
        clearCreateForm();
        refresh();
        showCredentials(issued);
    }

    private void resetPassword(AccountSummary summary) {
        IssuedCredentials issued = accounts.resetPassword(summary.id(), null, actor);
        refresh();
        showCredentials(issued);
    }

    private void editRoles(AccountSummary summary) {
        CheckboxGroup<String> choice = new CheckboxGroup<>("Extra roles");
        choice.setItemLabelGenerator(RoleLabels::plain);
        choice.setHelperText("Everyone can apply for their own leave; that needs no tick here.");
        choice.setItems(extraRoles());
        Set<String> current = new LinkedHashSet<>(summary.roles());
        current.remove("EMPLOYEE");
        choice.setValue(current);

        Dialog dialog = new Dialog(new H3("Roles for " + summary.username()), choice);
        Button save = new Button("Save", event -> Feedback.run("Roles updated.", () -> {
            accounts.replaceRoles(summary.id(), new ArrayList<>(choice.getValue()), actor);
            dialog.close();
            refresh();
        }));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /** Shows the temporary password once, with a copy button. */
    private void showCredentials(IssuedCredentials issued) {
        TextField usernameField = new TextField("Username");
        usernameField.setValue(issued.account().username());
        usernameField.setReadOnly(true);
        usernameField.setWidthFull();

        TextField passwordField = new TextField("Temporary password");
        passwordField.setValue(issued.temporaryPassword());
        passwordField.setReadOnly(true);
        passwordField.setWidthFull();

        Button copy = new Button("Copy password", event -> {
            passwordField.getElement().executeJs(
                    "navigator.clipboard.writeText($0)", issued.temporaryPassword());
            Feedback.success("Password copied.");
        });
        Button close = new Button("Done");
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Dialog dialog = new Dialog(
                new H3("Account ready"),
                new Paragraph("Give these details to the employee in person. "
                        + "The password is not shown again and must be changed at first sign-in."),
                usernameField, passwordField, new Span("Accounts already signed in keep their session until sign-out."));
        dialog.getFooter().add(copy, close);
        close.addClickListener(event -> dialog.close());
        dialog.open();
    }

    private void clearCreateForm() {
        candidate.clear();
        username.clear();
        temporaryPassword.clear();
        newRoles.clear();
    }

    private void refresh() {
        grid.setItems(accounts.search(search.getValue()));
        candidate.setItems(accounts.candidates());
    }
}
