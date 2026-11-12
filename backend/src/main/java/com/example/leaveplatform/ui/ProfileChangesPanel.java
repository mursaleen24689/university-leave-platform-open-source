package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;
import com.example.leaveplatform.profile.ProfileFieldChange;
import com.example.leaveplatform.profile.ProfileRequestDetail;
import com.example.leaveplatform.profile.ProfileRequestResponse;
import com.example.leaveplatform.profile.ProfileRequestService;
import com.example.leaveplatform.profile.ProfileRequestStatus;

/**
 * One proposed correction opened up: what the staff record says about each field
 * it asks about, side by side with what was asked for.
 *
 * <p>One component for both sides, because what they read is the same list and
 * only the buttons under it differ - that difference is one boolean decided by
 * the screen that builds it. The service still decides whether any of it is
 * allowed; hiding a button is not a rule.
 *
 * <p>The current value is read every time the panel is drawn rather than taken
 * from the request, so the office never decides against a record that has since
 * been corrected by hand.
 */
class ProfileChangesPanel extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.UK).withZone(ZoneId.systemDefault());

    private final ProfileRequestService requests;
    private final boolean desk;
    private final Supplier<String> username;
    private final Runnable listRefreshed;

    private final H3 heading = new H3();
    private final Paragraph note = new Paragraph();
    private final VerticalLayout lines = new VerticalLayout();
    private final TextArea comment = new TextArea("Note for the person");
    private final Button approve = new Button("Approve and correct the record");
    private final Button refuse = new Button("Refuse");
    private final Button withdraw = new Button("Withdraw this request");

    private Long requestId;

    ProfileChangesPanel(ProfileRequestService requests, boolean desk, Supplier<String> username,
            Runnable listRefreshed) {
        this.requests = requests;
        this.desk = desk;
        this.username = username;
        this.listRefreshed = listRefreshed;

        note.addClassName("app-muted");
        lines.addClassName("app-diff");
        lines.setPadding(false);
        lines.setSpacing(false);
        comment.setWidthFull();
        comment.setHelperText(desk
                ? "Say what you did, or why not. The person is sent these words."
                : "What the office said about your request.");
        comment.setReadOnly(!desk);

        approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        approve.addClickListener(event -> Feedback.run(this::approveRequest));
        refuse.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        refuse.addClickListener(event -> Feedback.run(this::refuseRequest));
        withdraw.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        withdraw.addClickListener(event -> Feedback.run(this::withdrawRequest));

        VerticalLayout controls = new VerticalLayout(
                desk ? Rows.wrap(approve, refuse) : withdraw);
        controls.setPadding(false);
        controls.setSpacing(false);

        add(heading, note, lines, comment, controls);
        setPadding(false);
        setAlignItems(Alignment.STRETCH);
        setVisible(false);
    }

    /** Loads one request and puts the panel in front of the person. */
    void open(Long id) {
        this.requestId = id;
        refresh();
        setVisible(true);
    }

    private void refresh() {
        if (requestId == null) {
            setVisible(false);
            return;
        }
        ProfileRequestDetail detail = requests.detail(username.get(), requestId);
        ProfileRequestResponse request = detail.request();
        boolean pending = request.status() == ProfileRequestStatus.PENDING;

        heading.setText(request.reference() + ": " + capital(request.changedLabels()));
        note.setText(summary(request));
        lines.removeAll();
        detail.changes().forEach(this::addLine);
        comment.setValue(request.decisionNote() == null ? "" : request.decisionNote());
        comment.setVisible(desk || request.decisionNote() != null);
        approve.setEnabled(pending);
        refuse.setEnabled(pending);
        withdraw.setEnabled(pending);
        setVisible(true);
    }

    /** The one line that says who is waiting for whom. */
    private String summary(ProfileRequestResponse request) {
        StringBuilder text = new StringBuilder();
        // The person reads the same panel the office does, so whose words the
        // reason is has to be obvious: the office is told what was asked, the
        // person is told what the answer was.
        text.append(request.statusLabel()).append(".");
        if (desk) {
            if (notBlank(request.reason())) {
                text.append(" They said: ").append(request.reason());
            }
            text.append(" Asked on ").append(WHEN.format(request.createdAt())).append(".");
            if (request.decidedByName() != null) {
                text.append(' ').append(request.decidedByName()).append(" answered on ")
                        .append(WHEN.format(request.decidedAt())).append(".");
            }
        } else {
            if (notBlank(request.decisionNote())) {
                text.append(" The office said: ").append(request.decisionNote());
            }
            text.append(" You asked on ").append(WHEN.format(request.createdAt())).append(".");
            if (request.decidedByName() != null) {
                text.append(' ').append(request.decidedByName()).append(" answered on ")
                        .append(WHEN.format(request.decidedAt())).append(".");
            }
        }
        return text.toString();
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    private void addLine(ProfileFieldChange change) {
        Span field = new Span(change.label());
        field.addClassName("app-diff-field");
        Span current = new Span("The record says: " + orNothing(change.current()));
        current.addClassName("app-diff-now");
        Span proposed = new Span("Asked for: " + change.proposed());
        proposed.addClassName("app-diff-asked");
        VerticalLayout row = new VerticalLayout(field, current, proposed);
        row.addClassName("app-diff-line");
        row.setPadding(false);
        row.setSpacing(false);
        lines.add(row);
    }

    private static String orNothing(String value) {
        return value == null || value.isBlank() ? "nothing yet" : value;
    }

    private static String capital(String text) {
        return text == null || text.isEmpty() ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void approveRequest() {
        requests.approve(username.get(), requestId, comment.getValue());
        Feedback.success("Approved. The record now says what was asked.");
        afterDecision();
    }

    private void refuseRequest() {
        requests.reject(username.get(), requestId, comment.getValue());
        Feedback.success("Refused, and the person has been told why.");
        afterDecision();
    }

    private void withdrawRequest() {
        requests.cancel(username.get(), requestId);
        Feedback.success("Withdrawn. Nothing was changed.");
        afterDecision();
    }

    private void afterDecision() {
        comment.clear();
        refresh();
        listRefreshed.run();
    }
}
