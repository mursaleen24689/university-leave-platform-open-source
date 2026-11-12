package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.RouterLink;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import com.example.leaveplatform.notification.NotificationEntry;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.notification.NotificationsResponse;

/**
 * The bell in the header: one number for what has not been read yet, and the
 * messages under it.
 *
 * <p>The count is read when the header is built and again whenever a message is
 * marked read. Nothing is watched in the background - a message appears the
 * moment somebody opens a screen, which is how the platform already works for
 * every other list, and a bell that only rings when a page is loaded is honest
 * about being a list of what happened rather than an alarm.
 */
class NotificationsBell extends HorizontalLayout implements BeforeLeaveObserver {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final NotificationService notifications;
    private final String username;
    private final Span badge = new Span();
    private final Span note = new Span();
    private final Dialog dialog = new Dialog();

    NotificationsBell(NotificationService notifications, String username) {
        this.notifications = notifications;
        this.username = username;

        Button bell = new Button();
        bell.setIcon(VaadinIcon.BELL.create());
        bell.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        bell.getElement().setAttribute("title", "Notifications");
        bell.getElement().setAttribute("aria-label", "Notifications");
        bell.addClickListener(event -> open());

        badge.addClassName("app-bell-count");
        note.addClassName("app-bell-note");
        add(bell, badge);
        addClassName("app-bell");
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        refresh();
    }

    /**
     * The messages were read on the screen the person is leaving, so the box does
     * not follow them onto the next one. Without this, clicking a message lands on
     * the approvals queue with the bell still covering it.
     */
    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        dialog.close();
    }

    /**
     * The bell is rebuilt every time it is opened, so what a person reads is what
     * the server holds at that moment - not what was true when the page was first
     * drawn.
     */
    private void open() {
        NotificationsResponse inbox = notifications.inbox(username, false);
        note.setText(ListNote.ofMessages(inbox.items().size(), inbox.unread()));
        dialog.removeAll();
        dialog.add(new H3("Notifications"), rows(inbox.items()), note, buttons());
        dialog.setWidth("min(36rem, 92vw)");
        dialog.setMaxHeight("80vh");
        dialog.open();
    }

    /**
     * One row per message, newest first - the order the server already put them
     * in. A person with nothing to read is told so rather than shown a blank box.
     */
    VerticalLayout rows(List<NotificationEntry> items) {
        VerticalLayout messages = new VerticalLayout();
        messages.addClassName("app-bell-list");
        messages.setPadding(false);
        messages.setSpacing(false);
        if (items.isEmpty()) {
            messages.add(new Paragraph("Nothing has been sent to you yet. A message appears here"
                    + " when a leave request reaches you, is recommended, or is decided."));
        } else {
            items.forEach(message -> messages.add(row(message)));
        }
        return messages;
    }

    private HorizontalLayout row(NotificationEntry message) {
        RouterLink title = new RouterLink(message.title(), target(message));
        title.addClassName("app-bell-title");
        VerticalLayout words = new VerticalLayout(title, new Span(message.body()),
                new Span(WHEN.format(message.createdAt())
                        + (message.unread() ? " - not read yet" : "")));
        words.addClassName("app-bell-message");
        words.setPadding(false);
        words.setSpacing(false);

        HorizontalLayout row = new HorizontalLayout(words);
        row.addClassName("app-bell-row");
        row.setAlignItems(Alignment.START);
        if (message.unread()) {
            Button read = new Button("Mark read", event -> {
                notifications.markRead(username, List.of(message.id()), message.kind());
                refresh();
                open();
            });
            read.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            row.add(read);
        }
        return row;
    }

    private HorizontalLayout buttons() {
        Button all = new Button("Mark all read", event -> {
            notifications.markAllRead(username);
            refresh();
            open();
        });
        all.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button close = new Button("Close", event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout buttons = new HorizontalLayout(all, close);
        buttons.addClassName("app-bell-actions");
        buttons.setPadding(false);
        return buttons;
    }

    private void refresh() {
        long unread = notifications.unreadCount(username);
        badge.setText(badgeFor(unread));
        badge.setVisible(unread > 0);
        badge.getElement().setAttribute("title", unread + " message(s) not read yet");
    }

    /**
     * A number nobody has to squint at: nothing when there is nothing, and a
     * ceiling so a hundred unread messages cannot push the header wider than the
     * page.
     */
    static String badgeFor(long unread) {
        if (unread <= 0) {
            return "";
        }
        return unread > 99 ? "99+" : String.valueOf(unread);
    }

    /**
     * Which screen a message belongs to. The kind decides it here rather than a
     * path being stored in the row, so a message can never point at a page the
     * platform does not have.
     */
    static Class<? extends Component> target(NotificationEntry message) {
        return switch (NotificationKind.valueOf(message.kind())) {
            case REQUEST_DECIDED -> MyLeaveView.class;
            case REQUEST_WAITING, REQUEST_RECOMMENDED, REQUEST_WITHDRAWN -> ApprovalsView.class;
            // The three ticket kinds carry the ticket number in their message id, and
            // that is not a leave request. Pointing a ticket message at /leave would
            // open a screen showing somebody else's dates, so they open the help desk.
            case TICKET_WAITING, TICKET_ANSWERED, TICKET_CLOSED -> TicketsView.class;
            // A request the office has to decide is read on the administration
            // screen, where the approve and refuse buttons are; the answer to it
            // is read on the screen that asked. Both take the number in the
            // message with them, and neither of them is a leave request.
            case PROFILE_WAITING -> AdminView.class;
            case PROFILE_APPROVED, PROFILE_REJECTED -> ProfileView.class;
            // All four delegation kinds carry a delegation number, which is none
            // of the above. They open the person's own delegations screen, where
            // both sides of every window - held and handed over - are listed.
            case DELEGATION_OFFERED, DELEGATION_REVOKED, DELEGATION_ENDED, DELEGATION_ACTED ->
                    DelegationsView.class;
        };
    }
}
