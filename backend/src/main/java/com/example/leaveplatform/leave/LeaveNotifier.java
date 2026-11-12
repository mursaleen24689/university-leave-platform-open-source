package com.example.leaveplatform.leave;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.example.leaveplatform.delegation.ApprovalDelegation;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Says who should hear about a leave event and in what words.
 *
 * <p>{@link LeaveService} keeps the rules and calls this at the same moments it
 * writes to the audit trail, inside the same transaction: a request that did not
 * happen leaves no message behind. The recipient is always an account, resolved
 * from the person involved - the supervisor stored on the request, the head of
 * department of the applicant's department, or the applicant themself - because
 * a message has to be addressed to somebody who can read it. A step taken under
 * a delegation reaches one more person - the one whose approving it was - so
 * nobody comes back from leave to decisions they never saw coming.
 */
@Component
class LeaveNotifier {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final AppUserRepository users;
    private final NotificationService notifications;

    LeaveNotifier(AppUserRepository users, NotificationService notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    /**
     * A new request. The supervisor on the request is asked to recommend it; a
     * head of department's own request skips that step, so the central
     * administrators are the ones whose queue it lands in.
     */
    void submitted(LeaveApplication application) {
        String about = requested(application.getEmployee(), application);
        Long supervisorId = application.getSupervisorEmployeeId();
        if (supervisorId != null) {
            send(usernameOf(supervisorId), null, NotificationKind.REQUEST_WAITING,
                    "A leave request is waiting for your recommendation",
                    about + " It is waiting for you at the supervisor step.", application.getId());
            return;
        }
        sendAll(accountsWith(AppRole.CENTRAL_ADMIN, null), null, NotificationKind.REQUEST_WAITING,
                "A leave request is waiting for the final decision",
                about + " A head of department's own request goes straight to the final decision.",
                application.getId());
    }

    /**
     * The supervisor's answer. A recommendation moves the request to the people
     * who decide it - the heads of department of that department, or the central
     * administrators when the department has none; a refusal ends it, so the
     * applicant hears about that. When the answer came from a delegatee, the
     * delegator hears too: their name is on the request, and they are the one
     * who would otherwise get back from leave to find decisions they never saw.
     */
    void supervisorDecided(LeaveApplication application, AppUser actor, ApprovalDelegation held) {
        if (held != null) {
            tellDelegator(held, actor, application);
        }
        if (application.getStatus() == LeaveStatus.REJECTED) {
            send(usernameOf(application.getEmployee().getId()), actor, NotificationKind.REQUEST_DECIDED,
                    "Your leave request was not recommended",
                    "Your " + span(application) + " was refused at the supervisor step by "
                            + actor.getEmployee().getFullName() + "." + remarks(application),
                    application.getId());
            return;
        }
        List<String> deciders = accountsWith(AppRole.HOD, application.getEmployee().getDepartment().getId());
        if (deciders.isEmpty()) {
            deciders = accountsWith(AppRole.CENTRAL_ADMIN, null);
        }
        sendAll(deciders, actor, NotificationKind.REQUEST_RECOMMENDED,
                "A supervisor recommended a leave request",
                actor.getEmployee().getFullName() + " recommended "
                        + requested(application.getEmployee(), application)
                        + " The final decision is waiting for you.",
                application.getId());
    }

    /** The final answer always goes to the person who asked; a delegator hears of a decision taken in their name. */
    void decided(LeaveApplication application, AppUser actor, ApprovalDelegation held) {
        if (held != null) {
            tellDelegator(held, actor, application);
        }
        boolean approved = application.getStatus() == LeaveStatus.APPROVED;
        String title = approved
                ? (application.isCompensated() ? "Your leave was approved without deducting days"
                        : "Your leave was approved")
                : "Your leave request was refused";
        send(usernameOf(application.getEmployee().getId()), actor, NotificationKind.REQUEST_DECIDED, title,
                "Your " + span(application) + " was " + (approved ? "approved" : "refused") + " by "
                        + actor.getEmployee().getFullName() + "."
                        + (approved && application.isCompensated()
                        ? " No days were taken from your balance." : "")
                        + remarks(application),
                application.getId());
    }

    /**
     * The applicant cancelled, so whoever was holding the request hears that it
     * is no longer in their queue. The status it had before the withdrawal says
     * whose queue that was.
     */
    void withdrawn(LeaveApplication application, LeaveStatus wasWaitingFor, AppUser actor) {
        if (wasWaitingFor == LeaveStatus.PENDING_SUPERVISOR) {
            send(usernameOf(application.getSupervisorEmployeeId()), actor, NotificationKind.REQUEST_WITHDRAWN,
                    "A leave request was withdrawn",
                    requested(application.getEmployee(), application)
                            + " You no longer need to recommend it.",
                    application.getId());
            return;
        }
        List<String> deciders = accountsWith(AppRole.HOD, application.getEmployee().getDepartment().getId());
        if (deciders.isEmpty()) {
            deciders = accountsWith(AppRole.CENTRAL_ADMIN, null);
        }
        sendAll(deciders, actor, NotificationKind.REQUEST_WITHDRAWN,
                "A leave request was withdrawn",
                requested(application.getEmployee(), application) + " It is out of the decision queue.",
                application.getId());
    }

    /**
     * A step was taken under this window, so the person whose approving it was
     * hears who did it and to which request. The click lands on their own
     * delegations screen, so the message quotes the request rather than pointing
     * at it - the number is not theirs to act on from here.
     */
    private void tellDelegator(ApprovalDelegation held, AppUser actor, LeaveApplication application) {
        send(usernameOf(held.getFromEmployee().getId()), actor, NotificationKind.DELEGATION_ACTED,
                "A request was handled under your delegation",
                actor.getEmployee().getFullName() + " acted for you on "
                        + requested(application.getEmployee(), application)
                        + " (" + held.getReference() + ").",
                held.getId());
    }

    /** The words that name a request: who asked, for how long, over which dates. */
    static String requested(Employee applicant, LeaveApplication application) {
        return applicant.getFullName() + " (" + applicant.getEmployeeNo() + ") asked for "
                + span(application) + ".";
    }

    static String span(LeaveApplication application) {
        return application.getDays() + " day(s) of " + application.getLeaveType().getCode()
                + " leave from " + DAY.format(application.getStartDate())
                + " to " + DAY.format(application.getEndDate());
    }

    private static String remarks(LeaveApplication application) {
        return application.getRemarks() == null ? "" : " Reason given: " + application.getRemarks();
    }

    private Optional<String> usernameOf(Long employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return users.findByEmployeeId(employeeId)
                .filter(AppUser::isEnabled)
                .map(AppUser::getUsername);
    }

    private List<String> accountsWith(String roleCode, Long departmentId) {
        List<AppUser> found = departmentId == null
                ? users.findEnabledWithRole(roleCode)
                : users.findEnabledWithRoleInDepartment(roleCode, departmentId);
        return found.stream().map(AppUser::getUsername).toList();
    }

    private void send(Optional<String> recipient, AppUser actor, NotificationKind kind, String title,
            String body, Long applicationId) {
        recipient.ifPresent(username -> send(username, actor, kind, title, body, applicationId));
    }

    private void sendAll(List<String> recipients, AppUser actor, NotificationKind kind, String title,
            String body, Long applicationId) {
        recipients.forEach(username -> send(username, actor, kind, title, body, applicationId));
    }

    /**
     * Nobody is told about an action they took themselves: a head of department
     * who decides their own request, or a supervisor in a queue that is their
     * own, would otherwise read their own name back in the bell.
     */
    private void send(String recipient, AppUser actor, NotificationKind kind, String title, String body,
            Long applicationId) {
        if (recipient == null || (actor != null && recipient.equals(actor.getUsername()))) {
            return;
        }
        notifications.notifyUser(recipient, kind, title, body, applicationId);
    }
}
