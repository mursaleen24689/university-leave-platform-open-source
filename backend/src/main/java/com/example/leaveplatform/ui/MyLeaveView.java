package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.leave.LeaveApplicationResponse;
import com.example.leaveplatform.leave.LeaveBalanceResponse;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveRuleException;
import com.example.leaveplatform.leave.LeaveService;
import com.example.leaveplatform.leave.SubmitLeaveRequest;
import com.example.leaveplatform.notification.NotificationService;

/**
 * The employee's own leave: the current balance, a request form and the
 * history of their requests. All rules are enforced by {@link LeaveService};
 * this screen only shows what the service returns.
 */
@Route("leave")
@PageTitle("My leave - University Leave Platform")
@PermitAll
public class MyLeaveView extends VerticalLayout {

    private final LeaveService leave;
    private final AuthenticationContext authentication;

    private final Paragraph balanceLine = new Paragraph();
    private final FilterRow filters = new FilterRow(true, "Filter", this::refresh);
    private final Paragraph shown = new Paragraph();
    private final DatePicker start = new DatePicker("First day of leave");
    private final DatePicker end = new DatePicker("Last day of leave");
    private final TextArea reason = new TextArea("Reason");
    private final Grid<LeaveApplicationResponse> grid = new Grid<>(LeaveApplicationResponse.class, false);

    public MyLeaveView(LeaveService leave, AuthenticationContext authentication,
            NotificationService notifications) {
        this.leave = leave;
        this.authentication = authentication;

        start.setRequiredIndicatorVisible(true);
        end.setHelperText("Leave blank for a single day. Each year has its own balance, so one request"
                + " cannot cross into the next year.");
        // The same rule the service enforces, at the picker: a last day before the
        // first day is not a request anybody can send, so it is not offered.
        start.addValueChangeListener(event -> {
            LocalDate from = event.getValue();
            end.setMin(from);
            if (leftBehind(from, end.getValue())) {
                end.clear();
            }
        });
        reason.setWidthFull();
        shown.addClassName("app-empty");

        Button submit = new Button("Submit request", event -> submit());
        HorizontalLayout dates = Rows.wrap(start, end);

        grid.addColumn(LeaveApplicationResponse::reference).setHeader("Reference").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::startDate).setHeader("From").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::endDate).setHeader("To").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::days).setHeader("Days").setAutoWidth(true);
        grid.addColumn(MyLeaveView::statusText).setHeader("Status").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::remarks).setHeader("Remarks").setAutoWidth(true);
        grid.addColumn(LeaveApplicationResponse::reason).setHeader("Reason").setAutoWidth(true);
        grid.addComponentColumn(this::actionButton).setHeader("Action").setAutoWidth(true);
        Grids.scrollable(grid);
        Grids.emptyText(grid, "No request this year yet. Use the form above to send the first one.");

        VerticalLayout content = new VerticalLayout(
                new H2("My leave"), balanceLine, new H3("Request leave"), dates, reason, submit,
                new H3("My requests"), filters, shown, grid, new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private void submit() {
        LocalDate from = start.getValue();
        if (from == null) {
            Feedback.error("Choose the first day of leave.");
            return;
        }
        Feedback.run(() -> {
            LeaveApplicationResponse saved = leave.submit(username(),
                    new SubmitLeaveRequest(from, end.getValue(), reason.getValue()));
            start.clear();
            end.clear();
            reason.clear();
            refresh();
            Feedback.success("Request sent: %d day(s) from %s, %s.".formatted(
                    saved.days(), saved.startDate(), saved.statusLabel()));
        });
    }

    private void refresh() {
        try {
            LeaveBalanceResponse balance = leave.balance(username());
            balanceLine.setText("%s %d: %d of %d day(s) left, %d used.".formatted(
                    balance.leaveTypeName(), balance.year(), balance.remaining(),
                    balance.allocated(), balance.used()));
            LeaveFilter filter = filters.value();
            var found = leave.myApplications(username(), filter);
            grid.setItems(found);
            shown.setText(ListNote.ofLeave(found.size(), filter));
            shown.setVisible(!found.isEmpty());
            Grids.emptyText(grid, filter.isAny()
                    ? "No request this year yet. Use the form above to send the first one."
                    : "Nothing matches %s. Widen the dates or choose another status."
                            .formatted(filter.describe()));
        } catch (LeaveRuleException exception) {
            Feedback.error(exception);
        } catch (RuntimeException exception) {
            Feedback.error("Could not load your leave. Please sign in again.");
        }
    }

    /** The row offers a withdraw only while the request is still waiting. */
    private Component actionButton(LeaveApplicationResponse application) {
        if (!application.status().isOpen()) {
            Span done = new Span("-");
            done.addClassName("app-muted");
            return done;
        }
        Button withdraw = new Button("Withdraw", event -> askWithdraw(application));
        withdraw.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        return withdraw;
    }

    private void askWithdraw(LeaveApplicationResponse application) {
        RemarkDialog.open(
                "Withdraw the %d day(s) from %s to %s?".formatted(
                        application.days(), application.startDate(), application.endDate()),
                "Nobody has decided it yet, so nothing is deducted from your balance. The request "
                        + "stays in your history as withdrawn.",
                "Withdraw", "Request withdrawn.",
                remarks -> leave.withdraw(username(), application.id(), remarks));
    }

    /**
     * Whether the last day of a request is now left behind by the first day.
     * Empty on either side is not left behind - a one-day request carries no
     * last day at all.
     */
    static boolean leftBehind(LocalDate from, LocalDate untilNow) {
        return from != null && untilNow != null && untilNow.isBefore(from);
    }

    private static String statusText(LeaveApplicationResponse application) {
        if (application.compensated()) {
            return application.statusLabel() + " (compensatory leave)";
        }
        return application.statusLabel();
    }

    private String username() {
        return authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
    }
}
