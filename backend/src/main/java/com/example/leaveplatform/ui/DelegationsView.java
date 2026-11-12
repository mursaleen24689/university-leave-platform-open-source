package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.delegation.ApprovalDelegation;
import com.example.leaveplatform.delegation.DelegateeResponse;
import com.example.leaveplatform.delegation.DelegationResponse;
import com.example.leaveplatform.delegation.DelegationScope;
import com.example.leaveplatform.delegation.DelegationService;
import com.example.leaveplatform.delegation.NewDelegationRequest;
import com.example.leaveplatform.notification.NotificationService;

/**
 * One screen for both sides of a handed-over window: what the signed-in person
 * has given to colleagues, and what colleagues have given to them. The same
 * list answers both questions because the same question - "whose approving is
 * this today" - is the one the Approvals screen asks too.
 *
 * <p>Hiding the revoke button from anyone but the delegator is presentation
 * only; {@link DelegationService#revoke} accepts the same call from the
 * delegator or the office and refuses it from everybody else, so the rule lives
 * in one place whichever door it is approached from.
 */
@Route("delegations")
@PageTitle("Delegated approvals - University Leave Platform")
@RolesAllowed({"SUPERVISOR", "HOD", "CENTRAL_ADMIN"})
public class DelegationsView extends VerticalLayout {

    private final DelegationService delegations;
    private final AuthenticationContext authentication;

    private final Paragraph note = new Paragraph();
    private final Grid<DelegationResponse> grid = new Grid<>(DelegationResponse.class, false);
    private final ComboBox<DelegateeResponse> colleague = new ComboBox<>("Colleague");
    private final Select<DelegationScope> scope = new Select<>();
    private final DatePicker from = new DatePicker("From");
    private final DatePicker to = new DatePicker("To");
    private final TextArea reason = new TextArea("Why");
    private final Button open = new Button("Hand the approving over");

    public DelegationsView(DelegationService delegations, AuthenticationContext authentication,
            NotificationService notifications) {
        this.delegations = delegations;
        this.authentication = authentication;

        grid.addColumn(DelegationResponse::reference).setHeader("Ref").setAutoWidth(true);
        grid.addColumn(row -> row.mine() ? "You → " + row.toName() : row.fromName() + " → you")
                .setHeader("Between").setAutoWidth(true);
        grid.addColumn(DelegationResponse::scopeLabel).setHeader("Covering").setAutoWidth(true);
        grid.addColumn(row -> row.departmentName() == null ? "-" : row.departmentName())
                .setHeader("Department").setAutoWidth(true);
        grid.addColumn(row -> row.startDate() + " to " + row.endDate()).setHeader("Window")
                .setAutoWidth(true);
        grid.addColumn(DelegationResponse::state).setHeader("Today").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader("Action").setAutoWidth(true);
        Grids.scrollable(grid);

        colleague.setItemLabelGenerator(DelegateeResponse::label);
        colleague.setHelperText("Only colleagues with a working sign-in account are listed;"
                + " the first " + DelegationService.DELEGATEE_LIMIT + " in name order.");

        scope.setLabel("Which approving");
        scope.setItems(DelegationScope.SUPERVISOR, DelegationScope.DECISION);
        scope.setItemLabelGenerator(DelegationScope::label);
        scope.setHelperText("Recommending: the requests of the staff who report to you."
                + " Final decision: your department's requests, as the head of department.");
        scope.setValue(DelegationScope.SUPERVISOR);

        from.setValue(LocalDate.now());
        to.setValue(LocalDate.now().plusDays(7));
        from.setMin(LocalDate.now());
        to.setMin(LocalDate.now());

        reason.setMaxLength(ApprovalDelegation.MAX_REASON);
        reason.setHelperText("Optional, in your words. The colleague and the audit trail both read it.");
        reason.setWidthFull();

        open.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        open.addClickListener(event -> Feedback.run(this::createWindow));

        HorizontalLayout row = Rows.wrap(colleague, scope, from, to);

        VerticalLayout form = new VerticalLayout(row, reason, open);
        form.addClassName("app-card");
        form.setPadding(false);
        form.setSpacing(false);

        VerticalLayout content = new VerticalLayout(
                new H2("Delegated approvals"),
                note,
                new H3("The windows"), grid,
                new H3("Open a window"), form,
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private void refresh() {
        // setItems drops the picked colleague, and a failed window should leave
        // the form as the person typed it rather than making them choose again.
        DelegateeResponse picked = colleague.getValue();
        colleague.setItems(delegations.colleagues(username(), null));
        colleague.setValue(picked);
        List<DelegationResponse> found = delegations.mine(username());
        grid.setItems(found);
        long giving = found.stream().filter(DelegationResponse::mine).count();
        long holding = found.stream().filter(row -> !row.mine() && row.usableToday()).count();
        note.setText(found.isEmpty()
                ? "No window has ever been opened in either direction. While you are away, your"
                    + " requests would wait for you - the form below hands them to a colleague"
                    + " for the days you name."
                : "%d window(s) %s. You have handed over %d and you are acting for %d %s.".formatted(
                        found.size(), found.size() == 1 ? "seen" : "seen",
                        giving, holding, holding == 1 ? "name today" : "names today"));
    }

    /** Only the person who opened a live window - or the office - can close it. */
    private HorizontalLayout actions(DelegationResponse delegation) {
        if (!delegation.mine() || !delegation.active()) {
            return Rows.wrap(new Span("-"));
        }
        Button revoke = new Button("Close now", event -> Feedback.run(() -> {
            delegations.revoke(username(), delegation.id());
            Feedback.success("Closed. The approving is back with you from today.");
            refresh();
        }));
        revoke.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        return Rows.wrap(revoke);
    }

    private void createWindow() {
        DelegateeResponse chosen = colleague.getValue();
        if (chosen == null) {
            Feedback.error("Choose the colleague first.");
            return;
        }
        delegations.create(username(), new NewDelegationRequest(
                chosen.id(), scope.getValue(), from.getValue(), to.getValue(),
                reason.getValue()));
        Feedback.success(chosen.fullName() + " can act for you from " + from.getValue()
                + " to " + to.getValue() + ". They have been told.");
        colleague.clear();
        reason.clear();
        refresh();
    }

    private String username() {
        return authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
    }
}
