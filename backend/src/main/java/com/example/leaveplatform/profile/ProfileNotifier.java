package com.example.leaveplatform.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Says who should hear about a proposed correction, and in what words.
 *
 * <p>{@link ProfileRequestService} keeps the rules and calls this in the same
 * transaction it writes in, so a request that did not happen leaves no message
 * behind. There is no queue of named officers as the help desk has: the people
 * told are the enabled central administrator accounts, which is the same group
 * the screens let approve. Nobody is told about a decision they made themselves,
 * and a person is not told about a request they just wrote.
 *
 * <p>The message carries the request id in the column the bell already uses for
 * "the row this message is about". That is only safe because of what a click on
 * each kind does: the office kinds open the administration screen and the staff
 * kinds open the person's own requests, so the number is never read as a leave
 * request. See {@code ui/NotificationsBell#target}.
 */
@Component
class ProfileNotifier {

    private final AppUserRepository users;
    private final NotificationService notifications;

    ProfileNotifier(AppUserRepository users, NotificationService notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    /** A new request arrives in the office's queue. */
    void waitingForOffice(ProfileRequest request, AppUser actor) {
        sendAll(desk(), actor, NotificationKind.PROFILE_WAITING,
                "A correction to a staff record is waiting",
                quoted(request) + " Asked by " + request.getEmployee().getFullName() + ".",
                request.getId());
    }

    /** The office agreed and the record now says what was asked. */
    void approved(ProfileRequest request, AppUser officer) {
        send(usernameOf(request.getEmployee()), officer, NotificationKind.PROFILE_APPROVED,
                "Your record was corrected",
                quoted(request) + " Your " + fields(request) + " " + (isOne(request) ? "is" : "are")
                        + " now what you asked for.",
                request.getId());
    }

    /** The office refused, and the reason is the useful part of the message. */
    void refused(ProfileRequest request, AppUser officer, String note) {
        send(usernameOf(request.getEmployee()), officer, NotificationKind.PROFILE_REJECTED,
                "Your request to correct your record was refused",
                quoted(request) + " The office said: " + note,
                request.getId());
    }

    private static boolean isOne(ProfileRequest request) {
        return request.changedFields().size() == 1;
    }

    private static String fields(ProfileRequest request) {
        return ProfileField.describe(request.changedFields());
    }

    private static String quoted(ProfileRequest request) {
        return request.getReference() + ": " + fields(request) + ".";
    }

    private List<String> desk() {
        return users.findEnabledWithRole(AppRole.CENTRAL_ADMIN).stream()
                .map(AppUser::getUsername)
                .toList();
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
            String body, Long requestId) {
        recipient.ifPresent(username -> send(username, actor, kind, title, body, requestId));
    }

    private void sendAll(List<String> recipients, AppUser actor, NotificationKind kind, String title,
            String body, Long requestId) {
        recipients.forEach(username -> send(username, actor, kind, title, body, requestId));
    }

    private void send(String recipient, AppUser actor, NotificationKind kind, String title, String body,
            Long requestId) {
        if (recipient == null || (actor != null && recipient.equals(actor.getUsername()))) {
            return;
        }
        notifications.notifyUser(recipient, kind, title, body, requestId);
    }
}
