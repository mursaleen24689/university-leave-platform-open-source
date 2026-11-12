package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.profile.ProfileChangesRequest;
import com.example.leaveplatform.profile.ProfileField;
import com.example.leaveplatform.profile.ProfileRecordResponse;
import com.example.leaveplatform.profile.ProfileRequestResponse;
import com.example.leaveplatform.profile.ProfileRequestService;

/**
 * The staff side of a record correction: read what the university holds, ask for
 * the part of it that is wrong to be put right, and follow the answer.
 *
 * <p>Nothing here can change a record by itself - the request only ever asks, and
 * the office decides. That is the point of the screen: a person who knows their
 * own number is wrong cannot usually reach the one account that is allowed to fix
 * it, and a note on paper gets lost.
 *
 * <p>The five boxes are blank on purpose. A box left blank is not part of the
 * request and its value on the record is left alone, which is the rule the
 * service keeps as {@code changed_fields}; a box filled with what it already says
 * is refused, because it asks the office to decide nothing.
 */
@Route("profile")
@PageTitle("My staff record - University Leave Platform")
@PermitAll
public class ProfileView extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final ProfileRequestService requests;
    private final Supplier<String> username;

    private final VerticalLayout held = new VerticalLayout();
    private final TextField name = new TextField("Full name");
    private final TextField staffNumber = new TextField("Staff number");
    private final TextField role = new TextField("Designation");
    private final TextField email = new TextField("E-mail");
    private final TextField phone = new TextField("Phone");
    private final TextArea reason = new TextArea("Why it needs putting right");
    private final Paragraph shown = new Paragraph();
    private final Grid<ProfileRequestResponse> grid = new Grid<>(ProfileRequestResponse.class, false);
    private final ProfileChangesPanel panel;

    public ProfileView(ProfileRequestService requests, AuthenticationContext authentication,
            NotificationService notifications) {
        this.requests = requests;
        this.username = () -> authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        this.panel = new ProfileChangesPanel(requests, false, username, this::refresh);

        held.addClassName("app-held");
        held.setPadding(false);
        held.setSpacing(false);
        List.of(name, staffNumber, role, email, phone).forEach(field -> field.setWidthFull());
        name.setMaxLength(ProfileField.FULL_NAME.maxLength());
        staffNumber.setMaxLength(ProfileField.EMPLOYEE_NO.maxLength());
        role.setMaxLength(ProfileField.DESIGNATION.maxLength());
        email.setMaxLength(ProfileField.EMAIL.maxLength());
        phone.setMaxLength(ProfileField.PHONE.maxLength());
        reason.setWidthFull();
        reason.setMaxLength(1000);
        reason.setHelperText("One or two sentences are enough. Never type a password here.");
        shown.addClassName("app-empty");

        Button submit = new Button("Send to the administration office", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        grid.addColumn(ProfileRequestResponse::reference).setHeader("Number").setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::changedLabels).setHeader("What you asked to change")
                .setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::statusLabel).setHeader("Status").setAutoWidth(true);
        grid.addColumn(request -> WHEN.format(request.createdAt())).setHeader("Asked on")
                .setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::decisionNote).setHeader("The office said")
                .setAutoWidth(true);
        grid.addComponentColumn(this::openButton).setHeader("Open").setAutoWidth(true);
        Grids.scrollable(grid);
        Grids.emptyText(grid, "You have not asked for a correction to your record yet.");

        VerticalLayout content = new VerticalLayout(
                new H2("My staff record"),
                new Paragraph("This is what the university holds about you for leave purposes. If a"
                        + " part of it is wrong, type the right value and send it to the"
                        + " administration office. Nothing changes until they agree."
                        + " A department transfer or a change of supervisor is not asked for here:"
                        + " the office makes those, because they decide whose requests you send to."),
                new H3("What the record says now"),
                held,
                new H3("Ask for a correction"),
                new Paragraph("Fill in only the part that is wrong. A box you leave blank is not"
                        + " part of the request, and the value beside it stays exactly as it is."),
                Rows.wrap(name, staffNumber),
                Rows.wrap(role, email),
                phone,
                reason,
                submit,
                new H3("My requests"),
                shown,
                grid,
                panel,
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private void submit() {
        Feedback.run(() -> {
            ProfileRequestResponse saved = requests.submit(username.get(),
                    new ProfileChangesRequest(name.getValue(), staffNumber.getValue(),
                            role.getValue(), email.getValue(), phone.getValue(), reason.getValue()));
            name.clear();
            staffNumber.clear();
            role.clear();
            email.clear();
            phone.clear();
            reason.clear();
            refresh();
            panel.open(saved.id());
            Feedback.success("Sent. The office will answer under number " + saved.reference() + ".");
        });
    }

    private void refresh() {
        ProfileRecordResponse now = requests.record(username.get());
        held.removeAll();
        held.add(line("Name", now.fullName()));
        held.add(line("Staff number", now.employeeNo()));
        held.add(line("Designation", now.designation()));
        held.add(line("Department", now.departmentName()));
        held.add(line("E-mail", now.email()));
        held.add(line("Phone", now.phone()));

        List<ProfileRequestResponse> found = requests.mine(username.get());
        grid.setItems(found);
        shown.setText("You have " + found.size() + " request(s) on this screen."
                + (found.size() >= ProfileRequestService.LIST_LIMIT
                        ? " Only the newest " + ProfileRequestService.LIST_LIMIT + " are listed." : ""));
        shown.setVisible(!found.isEmpty());
    }

    private static Span line(String label, String value) {
        Span line = new Span(label + ": " + (value == null || value.isBlank() ? "not recorded" : value));
        line.addClassName("app-held-line");
        return line;
    }

    private Button openButton(ProfileRequestResponse request) {
        Button open = new Button("Open", event -> panel.open(request.id()));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return open;
    }
}
