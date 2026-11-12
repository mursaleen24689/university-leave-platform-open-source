package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.leave.ApprovalQueueResponse;
import com.example.leaveplatform.leave.LeaveApplicationResponse;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveRuleException;
import com.example.leaveplatform.leave.LeaveService;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Two queues in one screen: requests sent to the signed-in user as supervisor,
 * and requests their department (or the whole university) still has to decide.
 * Only the roles that can act on a queue see anything in it, and the service
 * checks the same rules again on every action.
 */
@Route("approvals")
@PageTitle("Approvals - University Leave Platform")
@RolesAllowed({"SUPERVISOR", "HOD", "CENTRAL_ADMIN"})
public class ApprovalsView extends VerticalLayout {

    private final LeaveService leave;
    private final AuthenticationContext authentication;

    private final FilterRow filters = new FilterRow(false, "Filter", this::refresh);
    private final Paragraph supervisorNote = new Paragraph("Nothing is waiting for your recommendation.");
    private final Paragraph decisionNote = new Paragraph("Nothing is waiting for a final decision.");
    private final Grid<LeaveApplicationResponse> supervisorGrid = new Grid<>(LeaveApplicationResponse.class, false);
    private final Grid<LeaveApplicationResponse> decisionGrid = new Grid<>(LeaveApplicationResponse.class, false);

    public ApprovalsView(LeaveService leave, AuthenticationContext authentication,
            NotificationService notifications) {
        this.leave = leave;
        this.authentication = authentication;

        columns(supervisorGrid);
        supervisorGrid.addComponentColumn(this::supervisorActions).setHeader("Action");

        columns(decisionGrid);
        decisionGrid.addComponentColumn(this::decisionActions).setHeader("Action");

        VerticalLayout content = new VerticalLayout(
                new H2("Approvals"),
                new Paragraph("Both lists hold requests that are still waiting, so this screen has no status "
                        + "filter - only the dates. Leave a date blank to see everything."),
                filters,
                new H3("Waiting for my recommendation"), supervisorNote, supervisorGrid,
                new H3("Waiting for the final decision"), decisionNote, decisionGrid,
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private static void columns(Grid<LeaveApplicationResponse> grid) {
        grid.addColumn(LeaveApplicationResponse::reference).setHeader("Reference").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::employeeName).setHeader("Employee").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::departmentName).setHeader("Department").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::startDate).setHeader("From").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::endDate).setHeader("To").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::days).setHeader("Days").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::reason).setHeader("Reason").setAutoWidth(true);
        Grids.scrollable(grid);
    }

    private void refresh() {
        try {
            LeaveFilter period = filters.value();
            ApprovalQueueResponse queue = leave.approvals(username(), period);
            noteFor(supervisorNote, supervisorGrid, queue.supervisorQueue(), period, period.isAny()
                    ? "Nothing is waiting for your recommendation."
                    : "Nothing " + period.describe() + " is waiting for your recommendation.");
            noteFor(decisionNote, decisionGrid, queue.decisionQueue(), period, period.isAny()
                    ? "Nothing is waiting for a final decision."
                    : "Nothing " + period.describe() + " is waiting for a final decision.");
        } catch (LeaveRuleException exception) {
            Feedback.error(exception);
        } catch (RuntimeException exception) {
            Feedback.error("Could not load the queues. Please sign in again.");
        }
    }

    /**
     * One sentence above each queue: what is waiting now, and when the list was
     * cut off at the cap the person is told to narrow the dates.
     */
    private static void noteFor(Paragraph note, Grid<LeaveApplicationResponse> grid,
            List<LeaveApplicationResponse> found, LeaveFilter period, String whenEmpty) {
        grid.setItems(found);
        note.setText(found.isEmpty() ? whenEmpty : ListNote.ofLeave(found.size(), period));
        note.setVisible(true);
        grid.setVisible(!found.isEmpty());
    }

    /**
     * Supervisor step: recommend the request to the HOD, or reject it here.
     * Both need a reason, because the remark is what the applicant reads.
     */
    private HorizontalLayout supervisorActions(LeaveApplicationResponse application) {
        Button recommend = new Button("Recommend", event -> RemarkDialog.open(
                heading("Recommend", application), null, "Recommend", "Recommended.",
                remarks -> recommend(application, true, remarks)));
        recommend.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        Button reject = new Button("Reject", event -> RemarkDialog.open(
                heading("Reject", application), null, "Reject", "Rejected.",
                remarks -> recommend(application, false, remarks)));
        reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        return Rows.wrap(recommend, reject);
    }

    /**
     * Final decision. Compensatory approval grants the leave without spending
     * days from the balance, the way the earlier application marked a request as compensated.
     */
    private HorizontalLayout decisionActions(LeaveApplicationResponse application) {
        Button approve = new Button("Approve", event -> RemarkDialog.open(
                heading("Approve", application),
                "The %d day(s) will be deducted from their leave balance for this year."
                        .formatted(application.days()),
                "Approve", "Approved.",
                remarks -> decide(application, true, false, remarks)));
        approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        Button compensate = new Button("Approve as compensatory", event -> RemarkDialog.open(
                heading("Approve as compensatory leave", application),
                "The leave is granted, but no days are deducted from the balance.",
                "Approve without deduction", "Approved as compensatory leave.",
                remarks -> decide(application, true, true, remarks)));
        compensate.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        Button reject = new Button("Reject", event -> RemarkDialog.open(
                heading("Reject", application), null, "Reject", "Rejected.",
                remarks -> decide(application, false, false, remarks)));
        reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        return Rows.wrap(approve, compensate, reject);
    }

    private static String heading(String verb, LeaveApplicationResponse application) {
        return "%s %d day(s) for %s (%s to %s)?".formatted(
                verb, application.days(), application.employeeName(),
                application.startDate(), application.endDate());
    }

    private void recommend(LeaveApplicationResponse application, boolean agree, String remarks) {
        Feedback.run(() -> {
            leave.supervisorDecision(username(), application.id(), agree, remarks);
            refresh();
        });
    }

    private void decide(LeaveApplicationResponse application, boolean approve, boolean compensate,
            String remarks) {
        Feedback.run(() -> {
            leave.decide(username(), application.id(), approve, compensate, remarks);
            refresh();
        });
    }

    private String username() {
        return authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
    }
}
