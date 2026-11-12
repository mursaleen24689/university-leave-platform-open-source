package com.example.leaveplatform.notification;

import java.time.Instant;

/** One message as the bell and the API present it. */
public record NotificationEntry(Long id, String kind, String title, String body, Long applicationId,
        String screen, Instant createdAt, boolean unread) {

    static NotificationEntry of(Notification message) {
        return new NotificationEntry(message.getId(), message.getKind(), message.getTitle(),
                message.getBody(), message.getApplicationId(),
                NotificationKind.valueOf(message.getKind()).screen(),
                message.getCreatedAt(), message.isUnread());
    }
}
