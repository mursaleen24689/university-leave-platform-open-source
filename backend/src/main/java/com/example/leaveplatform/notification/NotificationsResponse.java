package com.example.leaveplatform.notification;

import java.util.List;

/** The bell's whole payload: the badge count and the list under it. */
public record NotificationsResponse(long unread, List<NotificationEntry> items) {
}
