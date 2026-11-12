package com.example.leaveplatform.notification;

import com.example.leaveplatform.notification.NotificationKind;

/**
 * A message that has just been written to somebody's bell.
 *
 * <p>Publishing this rather than calling the mail service from here is what keeps
 * a slow or broken mail server out of the leave transaction: the listener runs
 * only once the row is committed, so a message is never mailed about a request
 * that was rolled back, and a mail failure can never undo an approval.
 */
public record MessageStored(String recipientUsername, NotificationKind kind, String title,
        String body) {
}
