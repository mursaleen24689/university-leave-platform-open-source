package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditEntry;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Read-only view of the audit trail for the central administrator and the
 * auditor. The auditor holds no role that can change data, so this screen being
 * visible is the whole of the auditor's access.
 */
@Route("audit")
@PageTitle("Audit trail - University Leave Platform")
@RolesAllowed({"CENTRAL_ADMIN", "AUDITOR"})
public class AuditView extends VerticalLayout {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AuditService audit;
    private final TextField search = new TextField("Search");
    private final DatePicker from = new DatePicker("From");
    private final DatePicker to = new DatePicker("To");
    private final Grid<AuditEntry> grid = new Grid<>(AuditEntry.class, false);
    private final Paragraph summary = new Paragraph();

    public AuditView(AuditService audit, AuthenticationContext authentication,
            NotificationService notifications) {
        this.audit = audit;

        search.setPlaceholder("Name, action or detail, e.g. leave approved");
        search.setClearButtonVisible(true);
        search.setWidthFull();
        search.setMaxWidth("320px");
        search.addKeyPressListener(Key.ENTER, event -> refresh());

        // The same wording and the same blank-means-any rule as the leave lists,
        // so one screen does not ask for a date the other one does not understand.
        from.setHelperText("Blank: any date");
        to.setHelperText("Blank: any date");
        from.setWidthFull();
        from.setMaxWidth("170px");
        to.setWidthFull();
        to.setMaxWidth("170px");
        from.addValueChangeListener(event -> to.setMin(event.getValue()));
        to.addValueChangeListener(event -> from.setMax(event.getValue()));

        Button update = new Button("Search", event -> refresh());
        Button clear = new Button("Show everything", event -> {
            search.clear();
            from.clear();
            to.clear();
            refresh();
        });
        HorizontalLayout toolbar = Rows.wrap(search, from, to, update, clear);

        grid.addClassName("app-audit");
        grid.addColumn(entry -> TIME.format(entry.occurredAt()))
                .setHeader("When").setAutoWidth(true).setSortable(false);
        grid.addColumn(AuditEntry::actorUsername).setHeader("Who").setAutoWidth(true);
        grid.addColumn(entry -> ActionLabels.plain(entry.action())).setHeader("Action").setAutoWidth(true);
        grid.addColumn(entry -> ActionLabels.plain(entry.targetType())).setHeader("On").setAutoWidth(true);
        grid.addColumn(AuditEntry::detail).setHeader("Detail").setWidth("440px").setFlexGrow(1);
        grid.setWidthFull();
        grid.setMinHeight("320px");
        Grids.scrollable(grid, "30rem");
        Grids.emptyText(grid, "Nothing matches that search. The trail keeps every entry, so try a "
                + "username, an action such as leave approved, or a staff number.");

        VerticalLayout content = new VerticalLayout();
        content.addClassName("app-page");
        content.setPadding(false);
        content.add(new H2("Audit trail"), new Paragraph(
                "Every privileged action and every sign-in is recorded here. Entries are never edited "
                        + "or deleted. The latest 200 matching entries are shown."),
                toolbar, summary, grid, new CreditFooter());

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        refresh();
    }

    private void refresh() {
        try {
            List<AuditEntry> entries = audit.recent(search.getValue(), from.getValue(), to.getValue());
            grid.setItems(entries);
            summary.setText(entries.isEmpty()
                    ? "No entries match. Clear the dates or the word and try a staff number instead."
                    : entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies") + " shown"
                            + (entries.size() >= AuditService.LIST_LIMIT
                                    ? " - the newest " + AuditService.LIST_LIMIT
                                            + ", so narrow the dates to see the older ones."
                                    : "."));
        } catch (ResponseStatusException exception) {
            // A period the service refused, not a broken session: say which.
            Feedback.error(exception);
        }
    }
}
