package com.example.leaveplatform.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.example.leaveplatform.admin.AdminException;
import com.example.leaveplatform.auth.TwoStepRuleException;
import com.example.leaveplatform.delegation.DelegationRuleException;
import com.example.leaveplatform.leave.LeaveRuleException;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.identity.WeakPasswordException;
import com.example.leaveplatform.mail.MailSettingException;
import com.example.leaveplatform.profile.ProfileRuleException;
import com.example.leaveplatform.support.TicketRuleException;

/**
 * One place for the toast messages and for turning a rejected action into the
 * sentence the person can act on. Every screen used to carry its own copy of
 * this, which is how the wording drifted between them.
 *
 * <p>Only the exceptions that mean "the application refused this" are reported;
 * anything else is a bug and is left to propagate so it reaches the log.
 */
final class Feedback {

    private Feedback() {
    }

    /** Run an action and show a success toast when it completes. */
    static void run(String successMessage, Runnable action) {
        run(() -> {
            action.run();
            success(successMessage);
        });
    }

    /**
     * Run an action that reports its own outcome; only a refusal is turned into
     * an error toast here.
     */
    static void run(Runnable action) {
        try {
            action.run();
        } catch (AdminException | LeaveRuleException | WeakPasswordException
                | MailSettingException | TicketRuleException | ProfileRuleException
                | DelegationRuleException | TwoStepRuleException
                | ResponseStatusException exception) {
            error(exception);
        }
    }

    static void success(String message) {
        show(message, NotificationVariant.LUMO_SUCCESS);
    }

    static void error(String message) {
        show(message, NotificationVariant.LUMO_ERROR);
    }

    static void error(RuntimeException exception) {
        String message = exception instanceof ResponseStatusException status
                ? status.getReason()
                : exception.getMessage();
        show(message == null ? "The change could not be saved." : message,
                NotificationVariant.LUMO_ERROR);
    }

    private static void show(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 6000, Notification.Position.MIDDLE);
        notification.addThemeVariants(variant);
    }
}
