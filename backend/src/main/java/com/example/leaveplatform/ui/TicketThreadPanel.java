package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;
import com.example.leaveplatform.support.DeskMemberResponse;
import com.example.leaveplatform.support.TicketMessage;
import com.example.leaveplatform.support.TicketMessageResponse;
import com.example.leaveplatform.support.TicketResponse;
import com.example.leaveplatform.support.TicketService;
import com.example.leaveplatform.support.TicketStatus;
import com.example.leaveplatform.support.TicketThreadResponse;

/**
 * One ticket's conversation, shown the same way to the person who asked and to
 * the desk.
 *
 * <p>It is one component rather than two screens because the two sides read the
 * identical list; only the buttons under it differ, and that difference is one
 * boolean decided by the screen that builds it. The service still decides
 * whether any of it is allowed - hiding a button is not a rule.
 */
class TicketThreadPanel extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.UK).withZone(ZoneId.systemDefault());

    private final TicketService tickets;
    private final boolean desk;
    private final Supplier<String> username;
    private final Runnable listRefreshed;

    private final H3 heading = new H3();
    private final Paragraph note = new Paragraph();
    private final VerticalLayout lines = new VerticalLayout();
    private final TextArea message = new TextArea("Your line");
    private final Button send = new Button();
    private final ComboBox<DeskMemberResponse> handler = new ComboBox<>("Hand it to");
    private final Button assign = new Button("Assign");
    private final Button close = new Button("Close ticket");

    private Long ticketId;
    private boolean closed;

    TicketThreadPanel(TicketService tickets, boolean desk, Supplier<String> username,
            Runnable listRefreshed) {
        this.tickets = tickets;
        this.desk = desk;
        this.username = username;
        this.listRefreshed = listRefreshed;

        note.addClassName("app-muted");
        lines.addClassName("app-thread");
        lines.setPadding(false);
        lines.setSpacing(false);
        message.setWidthFull();
        message.setMaxLength(TicketMessage.MAX_BODY);
        message.setHelperText(desk
                ? "What the university answers. Never type a password or a security code here."
                : "Say what you need. Never type a password or a security code here.");

        send.addClickListener(event -> Feedback.run(this::sendLine));
        assign.addClickListener(event -> Feedback.run(this::assignTicket));
        assign.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        close.addClickListener(event -> Feedback.run(this::closeTicket));
        close.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        handler.setItemLabelGenerator(DeskMemberResponse::displayName);

        HorizontalLayout deskRow = desk ? Rows.wrap(handler, assign, close) : null;
        add(heading, note, lines, message, send);
        if (deskRow != null) {
            add(deskRow);
        }
        setPadding(false);
        setAlignItems(Alignment.STRETCH);
        setVisible(false);
    }

    /** Loads one ticket and puts the panel in front of the person. */
    void open(Long id) {
        this.ticketId = id;
        refresh();
        setVisible(true);
    }

    void clear() {
        ticketId = null;
        setVisible(false);
    }

    private void refresh() {
        if (ticketId == null) {
            setVisible(false);
            return;
        }
        TicketThreadResponse thread = tickets.thread(username.get(), ticketId);
        TicketResponse ticket = thread.ticket();
        closed = ticket.status() == TicketStatus.CLOSED;
        heading.setText(ticket.reference() + ": " + ticket.subject());
        note.setText(summary(ticket));
        lines.removeAll();
        thread.messages().forEach(this::addLine);
        message.setEnabled(!closed || !desk);
        send.setEnabled(!closed || !desk);
        send.setText(desk ? "Send answer" : (closed ? "Reopen this ticket" : "Send to the desk"));
        if (desk) {
            handler.setItems(tickets.deskMembers());
            handler.setEnabled(!closed);
            assign.setEnabled(!closed);
            close.setEnabled(!closed);
        }
        setVisible(true);
    }

    /** The one line that says whose turn it is - the question the queue answers. */
    private static String summary(TicketResponse ticket) {
        String asked = ticket.openedByName() + " (" + ticket.employeeNo() + ") of "
                + ticket.departmentName() + " asked on " + WHEN.format(ticket.createdAt()) + ".";
        String handling = ticket.assigneeName() == null
                ? " No name is on it yet."
                : " " + ticket.assigneeName() + " is handling it.";
        String when = ticket.status() == TicketStatus.CLOSED && ticket.closedAt() != null
                ? " Closed " + WHEN.format(ticket.closedAt()) + "."
                : "";
        return ticket.categoryLabel() + ". " + ticket.statusLabel() + "." + asked + handling + when;
    }

    private void addLine(TicketMessageResponse line) {
        Span who = new Span(line.authorName());
        who.addClassName("app-thread-author");
        Span mark = new Span(line.fromDesk() ? "help desk" : "staff");
        mark.addClassName("app-thread-desk");
        Span when = new Span(WHEN.format(line.createdAt()));
        when.addClassName("app-thread-when");
        Paragraph body = new Paragraph(line.body());
        HorizontalLayout row = new HorizontalLayout(who, mark, when);
        row.setPadding(false);
        row.setSpacing(false);
        VerticalLayout text = new VerticalLayout(row, body);
        text.addClassName("app-thread-line");
        text.setPadding(false);
        text.setSpacing(false);
        text.getStyle().set("flex", "1 1 auto");
        lines.add(text);
    }

    private void sendLine() {
        String body = message.getValue();
        if (desk) {
            tickets.replyByDesk(username.get(), ticketId, body);
        } else if (closed) {
            tickets.reopen(username.get(), ticketId, body);
        } else {
            tickets.replyByStaff(username.get(), ticketId, body);
        }
        message.clear();
        Feedback.success(desk ? "Answer sent." : (closed ? "Ticket reopened." : "Line sent."));
        refresh();
        listRefreshed.run();
    }

    private void assignTicket() {
        DeskMemberResponse chosen = handler.getValue();
        if (chosen == null) {
            Feedback.error("Choose who at the desk should handle this ticket.");
            return;
        }
        tickets.assign(username.get(), ticketId, chosen.username());
        Feedback.success("Ticket handed to " + chosen.displayName() + ".");
        refresh();
        listRefreshed.run();
    }

    private void closeTicket() {
        tickets.close(username.get(), ticketId, message.getValue());
        message.clear();
        Feedback.success("Ticket closed.");
        refresh();
        listRefreshed.run();
    }
}
