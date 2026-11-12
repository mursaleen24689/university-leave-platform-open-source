package com.example.leaveplatform.notification;

import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads the in-app messages behind the bell in the header.
 *
 * <p>Nothing here decides who should be told - that is the job of the module the
 * event belongs to (see {@code leave/LeaveNotifier}), which resolves the people
 * involved into accounts and passes the words. This service only stores what it
 * is given and hands a person their own messages, which keeps the wording of a
 * leave event in the leave module where the rules live.
 *
 * <p>Storing a message also publishes {@link MessageStored}. Publishing, rather
 * than calling the mail service, is what lets mail run after the row is
 * committed: a mail server that is down cannot undo an approval, and a request
 * that was rolled back is never mailed about.
 */
@Service
public class NotificationService {

    /** How many messages one read returns; the newest {@value} come back. */
    public static final int LIST_LIMIT = 200;

    private final NotificationRepository messages;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository messages, ApplicationEventPublisher events) {
        this.messages = messages;
        this.events = events;
    }

    @Transactional
    public void notifyUser(String recipientUsername, NotificationKind kind, String title, String body,
            Long applicationId) {
        String clippedTitle = clip(title, Notification.MAX_TITLE);
        String clippedBody = clip(body, Notification.MAX_BODY);
        messages.save(new Notification(recipientUsername, kind, clippedTitle, clippedBody, applicationId));
        events.publishEvent(new MessageStored(recipientUsername, kind, clippedTitle, clippedBody));
    }

    @Transactional(readOnly = true)
    public NotificationsResponse inbox(String username, boolean unreadOnly) {
        PageRequest page = PageRequest.of(0, LIST_LIMIT);
        List<Notification> found = unreadOnly
                ? messages.findUnreadFor(username, page)
                : messages.findAllFor(username, page);
        List<NotificationEntry> items = found.stream().map(NotificationEntry::of).toList();
        return new NotificationsResponse(messages.countByRecipientUsernameAndReadAtIsNull(username), items);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return messages.countByRecipientUsernameAndReadAtIsNull(username);
    }

    /**
     * Marks the given messages read, and only those of them that belong to the
     * person asking - an id copied from somebody else's inbox changes nothing.
     */
    @Transactional
    public int markRead(String username, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Notification> found = messages.findUnreadWithIds(username, ids);
        found.forEach(Notification::markRead);
        return found.size();
    }

    /**
     * Marks read only the listed messages that are of the given kind.
     *
     * <p>A screen that lists one subject's rows should not be able to mark rows
     * it never showed: the bell box and the help desk both deal with their own
     * messages, and one of them marking the other's as read would leave the
     * person with no way to find out that something arrived. A kind the platform
     * does not have marks nothing, which is the safe answer to a hand-typed name.
     */
    @Transactional
    public int markRead(String username, Collection<Long> ids, String kind) {
        if (ids == null || ids.isEmpty() || kind == null || kind.isBlank()) {
            return 0;
        }
        List<Notification> found = messages.findUnreadOfKindWithIds(username, ids, kind.trim());
        found.forEach(Notification::markRead);
        return found.size();
    }

    @Transactional
    public int markAllRead(String username) {
        List<Notification> found = messages.findUnreadFor(username, PageRequest.of(0, LIST_LIMIT));
        found.forEach(Notification::markRead);
        return found.size();
    }

    private static String clip(String text, int maximum) {
        if (text == null) {
            return null;
        }
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
