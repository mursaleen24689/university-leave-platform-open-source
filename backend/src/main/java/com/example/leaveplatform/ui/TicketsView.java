package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.support.SubmitTicketRequest;
import com.example.leaveplatform.support.SupportTicket;
import com.example.leaveplatform.support.TicketCategory;
import com.example.leaveplatform.support.TicketMessage;
import com.example.leaveplatform.support.TicketResponse;
import com.example.leaveplatform.support.TicketService;

/**
 * The staff side of the help desk: ask for help, follow the answer, and write
 * again if the problem came back.
 *
 * <p>The list is this person's own tickets only - {@link TicketService} takes
 * the name from the session, so there is no way to be shown a colleague's
 * conversation even by a hand-written address in the browser.
 */
@Route("tickets")
@PageTitle("Help desk - University Leave Platform")
@PermitAll
public class TicketsView extends VerticalLayout {

    private final TicketService tickets;
    private final Supplier<String> username;

    private final ComboBox<TicketCategory> category =
            new ComboBox<>("What do you need?", List.of(TicketCategory.values()));
    private final TextField subject = new TextField("One line saying what you need");
    private final TextArea detail = new TextArea("Details");
    private final Paragraph shown = new Paragraph();
    private final Grid<TicketResponse> grid = new Grid<>(TicketResponse.class, false);
    private final TicketThreadPanel thread;

    public TicketsView(TicketService tickets, AuthenticationContext authentication,
            NotificationService notifications) {
        this.tickets = tickets;
        this.username = () -> authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow();
        this.thread = new TicketThreadPanel(tickets, false, username, this::refresh);

        category.setItemLabelGenerator(TicketCategory::label);
        category.setRequiredIndicatorVisible(true);
        subject.setWidthFull();
        subject.setMaxLength(SupportTicket.MAX_SUBJECT);
        detail.setWidthFull();
        detail.setMaxLength(TicketMessage.MAX_BODY);
        detail.setHelperText("Dates, numbers and what you have already tried help the desk answer "
                + "in one go. Never type a password here.");
        shown.addClassName("app-empty");

        Button submit = new Button("Send to the help desk", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        grid.addColumn(TicketResponse::reference).setHeader("Number").setAutoWidth(true);
        grid.addColumn(TicketResponse::subject).setHeader("What you asked").setAutoWidth(true);
        grid.addColumn(TicketResponse::categoryLabel).setHeader("Kind").setAutoWidth(true);
        grid.addColumn(TicketResponse::statusLabel).setHeader("Status").setAutoWidth(true);
        grid.addColumn(TicketResponse::updatedAt).setHeader("Last word").setAutoWidth(true);
        grid.addComponentColumn(this::openButton).setHeader("Open").setAutoWidth(true);
        Grids.scrollable(grid);
        Grids.emptyText(grid, "You have not asked the help desk for anything yet.");

        VerticalLayout content = new VerticalLayout(
                new H2("Help desk"),
                new Paragraph("Anything about your leave record, your account or the platform itself "
                        + "goes here, and the administration office answers it in the same place. The "
                        + "conversation stays on this screen, so nothing is forgotten between a phone "
                        + "call and a visit."),
                new H3("Ask for help"),
                Rows.wrap(category),
                subject,
                detail,
                submit,
                new H3("My requests"),
                shown,
                grid,
                thread,
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private void submit() {
        if (category.getValue() == null) {
            Feedback.error("Choose what the ticket is about.");
            return;
        }
        Feedback.run(() -> {
            TicketResponse saved = tickets.submit(username.get(),
                    new SubmitTicketRequest(category.getValue().name(), subject.getValue(),
                            detail.getValue()));
            subject.clear();
            detail.clear();
            refresh();
            thread.open(saved.id());
            Feedback.success("Sent. The desk will answer under number " + saved.reference() + ".");
        });
    }

    private void refresh() {
        List<TicketResponse> found = tickets.mine(username.get());
        grid.setItems(found);
        shown.setText("You have " + found.size() + " ticket(s) on this screen."
                + (found.size() >= TicketService.LIST_LIMIT
                        ? " Only the newest " + TicketService.LIST_LIMIT + " are listed." : ""));
        shown.setVisible(!found.isEmpty());
    }

    private Button openButton(TicketResponse ticket) {
        Button open = new Button("Open", event -> thread.open(ticket.id()));
        open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return open;
    }
}
