package com.example.leaveplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

class NotificationServiceTest {

    private static final String RECIPIENT = "hod.mech";

    private final NotificationRepository messages = mock(NotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationService service = new NotificationService(messages, events);

    /**
     * The bell row and the mail hook are the same event seen from two sides, so
     * the words handed to the listener must be the words that were stored -
     * including the cut-down ones, because a mail that promises a sentence the
     * database never kept is a message nobody can verify.
     */
    @Test
    void aStoredMessageIsPublishedSoMailCanFollowIt() {
        service.notifyUser(RECIPIENT, NotificationKind.REQUEST_DECIDED, "x".repeat(250), "y".repeat(1200), 3L);

        ArgumentCaptor<MessageStored> published = ArgumentCaptor.forClass(MessageStored.class);
        verify(events).publishEvent(published.capture());
        MessageStored event = published.getValue();
        assertThat(event.recipientUsername()).isEqualTo(RECIPIENT);
        assertThat(event.kind()).isEqualTo(NotificationKind.REQUEST_DECIDED);
        assertThat(event.title()).hasSize(Notification.MAX_TITLE);
        assertThat(event.body()).hasSize(Notification.MAX_BODY);
    }

    @Test
    void readingTheBellNeverPublishesAnything() {
        service.inbox(RECIPIENT, false);
        service.markAllRead(RECIPIENT);

        verify(events, never()).publishEvent(any(MessageStored.class));
    }

    @Test
    void aMessageIsStoredForTheAccountItIsAddressedTo() {
        service.notifyUser(RECIPIENT, NotificationKind.REQUEST_WAITING,
                "A leave request is waiting for your recommendation", "Test Employee asked.", 7L);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(messages).save(saved.capture());
        Notification written = saved.getValue();
        assertThat(written.getRecipientUsername()).isEqualTo(RECIPIENT);
        assertThat(written.getKind()).isEqualTo(NotificationKind.REQUEST_WAITING.name());
        assertThat(written.getTitle()).isEqualTo("A leave request is waiting for your recommendation");
        assertThat(written.getBody()).isEqualTo("Test Employee asked.");
        assertThat(written.getApplicationId()).isEqualTo(7L);
        assertThat(written.getCreatedAt()).isNotNull();
        assertThat(written.isUnread()).isTrue();
    }

    @Test
    void wordsLongerThanTheColumnAreCutRatherThanRefused() {
        service.notifyUser(RECIPIENT, NotificationKind.REQUEST_DECIDED, "x".repeat(250), "y".repeat(1200), 1L);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getTitle()).hasSize(Notification.MAX_TITLE);
        assertThat(saved.getValue().getBody()).hasSize(Notification.MAX_BODY);
    }

    @Test
    void theInboxCarriesTheBadgeCountAndWhichLinesStillNeedReading() {
        Notification read = message("Your leave was approved", NotificationKind.REQUEST_DECIDED);
        read.markRead();
        Notification waiting = message("A leave request is waiting", NotificationKind.REQUEST_WAITING);
        when(messages.findAllFor(eq(RECIPIENT), any())).thenReturn(List.of(read, waiting));
        when(messages.countByRecipientUsernameAndReadAtIsNull(RECIPIENT)).thenReturn(1L);

        NotificationsResponse inbox = service.inbox(RECIPIENT, false);

        assertThat(inbox.unread()).isEqualTo(1L);
        assertThat(inbox.items()).hasSize(2);
        assertThat(inbox.items().get(0).unread()).isFalse();
        assertThat(inbox.items().get(0).screen()).isEqualTo("leave");
        assertThat(inbox.items().get(1).unread()).isTrue();
        assertThat(inbox.items().get(1).screen()).isEqualTo("approvals");
        assertThat(inbox.items().get(1).title()).isEqualTo("A leave request is waiting");
    }

    @Test
    void anUnreadOnlyInboxAsksTheDatabaseForTheUnreadRows() {
        when(messages.findUnreadFor(eq(RECIPIENT), any()))
                .thenReturn(List.of(message("A request was withdrawn", NotificationKind.REQUEST_WITHDRAWN)));
        when(messages.countByRecipientUsernameAndReadAtIsNull(RECIPIENT)).thenReturn(1L);

        NotificationsResponse inbox = service.inbox(RECIPIENT, true);

        assertThat(inbox.items()).hasSize(1);
        assertThat(inbox.items().get(0).kind()).isEqualTo(NotificationKind.REQUEST_WITHDRAWN.name());
        verify(messages, never()).findAllFor(any(), any());
    }

    @Test
    void markingNothingSelectedChangesNothing() {
        assertThat(service.markRead(RECIPIENT, List.of())).isZero();
        assertThat(service.markRead(RECIPIENT, null)).isZero();
        verify(messages, never()).findUnreadWithIds(any(), any());
    }

    @Test
    void aScreenThatShowsOneSubjectMarksOnlyItsOwnKind() {
        Notification ticket = message("The help desk answered your ticket",
                NotificationKind.TICKET_ANSWERED);
        when(messages.findUnreadOfKindWithIds(RECIPIENT, List.of(4L, 5L),
                NotificationKind.TICKET_ANSWERED.name())).thenReturn(List.of(ticket));

        assertThat(service.markRead(RECIPIENT, List.of(4L, 5L),
                NotificationKind.TICKET_ANSWERED.name())).isEqualTo(1);

        verify(messages, never()).findUnreadWithIds(any(), any());
        assertThat(ticket.isUnread()).isFalse();
    }

    @Test
    void aKindThePlatformDoesNotHaveMatchesNothingRatherThanEverything() {
        assertThat(service.markRead(RECIPIENT, List.of(4L), "NOT_A_KIND")).isZero();

        verify(messages).findUnreadOfKindWithIds(RECIPIENT, List.of(4L), "NOT_A_KIND");
        verify(messages, never()).findUnreadWithIds(any(), any());
    }

    @Test
    void aBlankKindMarksNothingInsteadOfFallingBackToEverything() {
        assertThat(service.markRead(RECIPIENT, List.of(4L), "   ")).isZero();
        assertThat(service.markRead(RECIPIENT, List.of(4L), null)).isZero();
        verify(messages, never()).findUnreadOfKindWithIds(any(), any(), any());
    }

    @Test
    void aTicketMessageOpensTheHelpDeskAndNotSomebodyElsesLeave() {
        Notification ticket = message("Your help-desk ticket was closed",
                NotificationKind.TICKET_CLOSED);

        assertThat(NotificationEntry.of(ticket).screen()).isEqualTo("tickets");
    }

    /**
     * An id copied from somebody else's inbox is not this person's message: the
     * repository is asked for the recipient's own unread rows with those ids, and
     * only what comes back is stamped.
     */
    @Test
    void onlyTheRecipientsOwnUnreadMessagesAreMarked() {
        Notification mine = message("A leave request is waiting", NotificationKind.REQUEST_WAITING);
        when(messages.findUnreadWithIds(eq(RECIPIENT), eq(List.of(4L, 5L)))).thenReturn(List.of(mine));

        assertThat(service.markRead(RECIPIENT, List.of(4L, 5L))).isEqualTo(1);
        assertThat(mine.getReadAt()).isNotNull();
    }

    @Test
    void markingTheBellClearsEverythingStillUnread() {
        when(messages.findUnreadFor(eq(RECIPIENT), any(Pageable.class)))
                .thenReturn(List.of(message("One", NotificationKind.REQUEST_RECOMMENDED),
                        message("Two", NotificationKind.REQUEST_DECIDED)));

        assertThat(service.markAllRead(RECIPIENT)).isEqualTo(2);
    }

    @Test
    void aSecondReadDoesNotMoveTheStampOfTheFirst() {
        Notification message = message("One", NotificationKind.REQUEST_WAITING);
        message.markRead();
        Instant first = message.getReadAt();
        message.markRead();

        assertThat(message.getReadAt()).isEqualTo(first);
        assertThat(message.isUnread()).isFalse();
    }

    private static Notification message(String title, NotificationKind kind) {
        return new Notification(RECIPIENT, kind, title, "Body", null);
    }
}
