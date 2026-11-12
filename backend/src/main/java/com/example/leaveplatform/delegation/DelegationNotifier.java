package com.example.leaveplatform.delegation;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Says who should hear about a handed-over window, and in what words.
 *
 * <p>{@link DelegationService} keeps the rules and calls this in the same
 * transaction it writes in, so a window that was never opened leaves no message
 * behind. Both sides always hear: the delegatee because the approving is now
 * theirs to notice, the delegator because the one thing worse than a queue
 * nobody sees is a queue nobody sees <i>and</i> nobody knows was handed over.
 * A person is never told about a thing they did themselves - a head of
 * department who closes their own window reads their own name back in the bell
 * and learns nothing.
 *
 * <p>The message carries the delegation id in the column the bell already uses
 * for "the row this message is about". That is only safe because of what a click
 * on each delegation kind does: all four open the person's own delegations
 * screen, so the number is never read as a leave request or a ticket.
 * See {@code ui/NotificationsBell#target}.
 */
@Component
class DelegationNotifier {

    private final AppUserRepository users;
    private final NotificationService notifications;

    DelegationNotifier(AppUserRepository users, NotificationService notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    /** A colleague can now act under this window - the dates and the reason are the news. */
    void offered(ApprovalDelegation delegation, AppUser actor) {
        send(usernameOf(delegation.getToEmployee()), actor, NotificationKind.DELEGATION_OFFERED,
                delegation.getFromEmployee().getFullName() + " has given you their approving",
                words(delegation) + " You are the one they named.",
                delegation.getId());
    }

    /**
     * The window is closed early. The holder hears it first, because their queue
     * is about to shrink; when the office closed it over somebody's head, the
     * delegator hears too, because it was their decision being handed back.
     */
    void revoked(ApprovalDelegation delegation, AppUser actor) {
        send(usernameOf(delegation.getToEmployee()), actor, NotificationKind.DELEGATION_REVOKED,
                "A delegated window was closed early",
                quoted(delegation) + " " + delegation.getScope().label() + " of "
                        + delegation.getFromEmployee().getFullName() + " is back with them from today.",
                delegation.getId());
        Long actorEmployee = actor == null || actor.getEmployee() == null
                ? null : actor.getEmployee().getId();
        if (!delegation.getFromEmployee().getId().equals(actorEmployee)) {
            send(usernameOf(delegation.getFromEmployee()), actor, NotificationKind.DELEGATION_ENDED,
                    "Your delegated window was closed",
                    quoted(delegation) + " The administration office handed your "
                            + delegation.getScope().label().toLowerCase(java.util.Locale.ROOT)
                            + " back to you.",
                    delegation.getId());
        }
    }

    private static String quoted(ApprovalDelegation delegation) {
        return delegation.getReference() + ": ";
    }

    private static String words(ApprovalDelegation delegation) {
        return quoted(delegation) + delegation.getScope().label() + " from " + delegation.getStartDate()
                + " to " + delegation.getEndDate()
                + (delegation.getDepartment() == null ? ""
                        : " for " + delegation.getDepartment().getName())
                + (delegation.getReason() == null ? "." : ". Reason given: " + delegation.getReason());
    }

    private Optional<String> usernameOf(Employee person) {
        if (person == null) {
            return Optional.empty();
        }
        return users.findByEmployeeId(person.getId())
                .filter(AppUser::isEnabled)
                .map(AppUser::getUsername);
    }

    private void send(Optional<String> recipient, AppUser actor, NotificationKind kind, String title,
            String body, Long delegationId) {
        recipient.ifPresent(username -> send(username, actor, kind, title, body, delegationId));
    }

    /**
     * Nobody is told about an action they took themselves; the bell would only
     * repeat their own name back at them.
     */
    private void send(String recipient, AppUser actor, NotificationKind kind, String title, String body,
            Long delegationId) {
        if (recipient == null || (actor != null && recipient.equals(actor.getUsername()))) {
            return;
        }
        notifications.notifyUser(recipient, kind, title, body, delegationId);
    }
}
