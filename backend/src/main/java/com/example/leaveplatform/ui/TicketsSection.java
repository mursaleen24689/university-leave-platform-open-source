package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;
import com.example.leaveplatform.support.TicketResponse;
import com.example.leaveplatform.support.TicketService;

/**
 * The desk's card on the administration screen: what is waiting, who has it, and
 * the conversation behind each line.
 *
 * <p>The queue is oldest first, which is the opposite of every other list in the
 * platform, because the question here is not "what happened last" but "whose
 * answer has been kept waiting longest". Ticking the box shows everything,
 * including what has been closed, for the person who wants the whole picture.
 */
class TicketsSection extends VerticalLayout {

    private final TicketService tickets;
    private final String actor;

    private final Paragraph note = new Paragraph();
    private final Checkbox showAll = new Checkbox("Show every ticket, including closed ones");
    private final Grid<TicketResponse> grid = new Grid<>(TicketResponse.class, false);
    private final TicketThreadPanel thread;

    TicketsSection(TicketService tickets, String actor) {
        this.tickets = tickets;
        this.actor = actor;
        this.thread = new TicketThreadPanel(tickets, true, () -> actor, this::refresh);

        note.addClassName("app-muted");
        showAll.addValueChangeListener(event -> refresh());

        grid.addColumn(TicketResponse::reference).setHeader("Number").setAutoWidth(true);
        grid.addColumn(TicketResponse::subject).setHeader("What was asked").setAutoWidth(true);
        grid.addColumn(TicketResponse::openedByName).setHeader("Asked by").setAutoWidth(true);
        grid.addColumn(TicketResponse::departmentName).setHeader("Department").setAutoWidth(true);
        grid.addColumn(TicketResponse::categoryLabel).setHeader("Kind").setAutoWidth(true);
        grid.addColumn(TicketResponse::statusLabel).setHeader("Status").setAutoWidth(true);
        grid.addColumn(TicketResponse::assigneeName).setHeader("Handled by").setAutoWidth(true);
        grid.addComponentColumn(this::openButton).setHeader("Open").setAutoWidth(true);
        Grids.scrollable(grid);

        Paragraph description = new Paragraph(
                "A staff member asks here and the answer goes back to them. Assign a ticket to one"
                    + " name so two people do not answer it twice, and close it when the matter is"
                    + " finished - the person who asked is told at each step.");
        description.addClassName("app-muted");

        add(new H3("Help desk"), description, note, showAll, grid, thread);
        addClassName("app-card");
        setWidthFull();
        setPadding(false);
        refresh();
    }

    private void refresh() {
        List<TicketResponse> found = showAll.getValue() ? tickets.all() : tickets.queue();
        grid.setItems(found);
        long waiting = tickets.waitingForDesk();
        note.setText(waiting == 0
                ? "Nothing is waiting for the desk."
                : "%d ticket(s) %s an answer from the desk. Listed: %d.".formatted(
                        waiting, waiting == 1 ? "is waiting for" : "are waiting for", found.size())
                        + (found.size() >= TicketService.LIST_LIMIT
                                ? " Only the first " + TicketService.LIST_LIMIT + " are listed." : ""));
        Grids.emptyText(grid, showAll.getValue()
                ? "No ticket has been opened yet."
                : "Nothing is waiting for the desk.");
    }

    private Button openButton(TicketResponse ticket) {
        Button open = new Button("Open", event -> thread.open(ticket.id()));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return open;
    }
}
