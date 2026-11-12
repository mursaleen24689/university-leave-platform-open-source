package com.example.leaveplatform.notification;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for the bell. There is no username in the address or the body:
 * the caller is whoever the session belongs to, and {@link NotificationService}
 * only ever reads or marks that person's own messages.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    NotificationsResponse inbox(Authentication authentication,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notifications.inbox(authentication.getName(), unreadOnly);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(Authentication authentication, @Valid @RequestBody MarkReadRequest request) {
        if (request.kind() == null || request.kind().isBlank()) {
            notifications.markRead(authentication.getName(), request.ids());
            return;
        }
        notifications.markRead(authentication.getName(), request.ids(), request.kind());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readAll(Authentication authentication) {
        notifications.markAllRead(authentication.getName());
    }
}
