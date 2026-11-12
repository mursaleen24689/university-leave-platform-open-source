package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.example.leaveplatform.profile.ProfileRequestResponse;
import com.example.leaveplatform.profile.ProfileRequestService;

/**
 * The office's card on the administration screen: which records people say are
 * wrong, and the comparison behind each one.
 *
 * <p>The queue is longest waiting first, like the help desk, because the question
 * here is not what arrived last but what has been waited on longest. Approving
 * re-checks the values, so a request that has been overtaken by events - a staff
 * number that has gone to somebody else in the meantime - is refused here with a
 * sentence rather than failing on a database constraint.
 */
class ProfileSection extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final ProfileRequestService requests;
    private final String actor;

    private final Paragraph note = new Paragraph();
    private final Checkbox showAll = new Checkbox("Show every request, including answered ones");
    private final Grid<ProfileRequestResponse> grid = new Grid<>(ProfileRequestResponse.class, false);
    private final ProfileChangesPanel panel;

    ProfileSection(ProfileRequestService requests, String actor) {
        this.requests = requests;
        this.actor = actor;
        this.panel = new ProfileChangesPanel(requests, true, () -> actor, this::refresh);

        note.addClassName("app-muted");
        showAll.addValueChangeListener(event -> refresh());

        grid.addColumn(ProfileRequestResponse::reference).setHeader("Number").setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::employeeName).setHeader("Person").setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::employeeNo).setHeader("Staff no").setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::departmentName).setHeader("Department").setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::changedLabels).setHeader("Wants to change")
                .setAutoWidth(true);
        grid.addColumn(ProfileRequestResponse::statusLabel).setHeader("Status").setAutoWidth(true);
        grid.addColumn(request -> WHEN.format(request.createdAt())).setHeader("Waiting since")
                .setAutoWidth(true);
        grid.addComponentColumn(this::openButton).setHeader("Decide").setAutoWidth(true);
        Grids.scrollable(grid);

        Paragraph description = new Paragraph(
                "A member of staff says part of their record is wrong and proposes the right value."
                    + " Open a request to see the record beside the proposal, then approve it - which"
                    + " changes only the fields that were asked about - or refuse it with a reason the"
                    + " person is sent.");
        description.addClassName("app-muted");

        add(new H3("Record corrections"), description, note, showAll, grid, panel);
        addClassName("app-card");
        setWidthFull();
        setPadding(false);
        refresh();
    }

    private void refresh() {
        List<ProfileRequestResponse> found = showAll.getValue() ? requests.all() : requests.queue();
        grid.setItems(found);
        long waiting = requests.waitingForOffice();
        note.setText(waiting == 0
                ? "Nothing is waiting for the office."
                : "%d %s a decision. Listed: %d.".formatted(
                        waiting,
                        waiting == 1 ? "request is waiting for" : "requests are waiting for",
                        found.size())
                        + (found.size() >= ProfileRequestService.LIST_LIMIT
                                ? " Only the first " + ProfileRequestService.LIST_LIMIT + " are listed."
                                : ""));
        Grids.emptyText(grid, showAll.getValue()
                ? "Nobody has asked for a correction to their record yet."
                : "Nothing is waiting for the office.");
    }

    private Button openButton(ProfileRequestResponse request) {
        Button open = new Button("Open", event -> panel.open(request.id()));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return open;
    }
}
