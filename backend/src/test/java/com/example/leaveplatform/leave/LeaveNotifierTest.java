package com.example.leaveplatform.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.delegation.ApprovalDelegation;
import com.example.leaveplatform.delegation.DelegationScope;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.organization.Department;

class LeaveNotifierTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);
    private static final Long APPLICANT_ID = 10L;
    private static final Long SUPERVISOR_ID = 20L;
    private static final Long DEPARTMENT_ID = 1L;
    private static final String APPLICANT_ACCOUNT = "employee.one";
    private static final String SUPERVISOR_ACCOUNT = "supervisor.one";
    private static final String HOD_ACCOUNT = "hod.mech";
    private static final String ADMIN_ACCOUNT = "admin";
    private static final Long ABSENT_ID = 30L;
    private static final String ABSENT_ACCOUNT = "hod.on.leave";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final LeaveNotifier notifier = new LeaveNotifier(users, notifications);

    private final Department department = mock(Department.class);
    private final Employee applicantEmployee = mock(Employee.class);
    private final LeaveType type = mock(LeaveType.class);

    private final AppUser applicant = account(APPLICANT_ACCOUNT);
    private final AppUser supervisor = account(SUPERVISOR_ACCOUNT);
    private final AppUser hod = account(HOD_ACCOUNT);
    private final AppUser centralAdmin = account(ADMIN_ACCOUNT);

    @BeforeEach
    void commonStubs() {
        when(department.getId()).thenReturn(DEPARTMENT_ID);
        when(applicantEmployee.getId()).thenReturn(APPLICANT_ID);
        when(applicantEmployee.getEmployeeNo()).thenReturn("EMP-001");
        when(applicantEmployee.getFullName()).thenReturn("Test Employee");
        when(applicantEmployee.getDepartment()).thenReturn(department);
        when(type.getCode()).thenReturn(LeaveType.CASUAL);
        when(users.findByEmployeeId(APPLICANT_ID)).thenReturn(Optional.of(applicant));
        when(users.findByEmployeeId(SUPERVISOR_ID)).thenReturn(Optional.of(supervisor));
        AppUser absent = account(ABSENT_ACCOUNT);
        when(users.findByEmployeeId(ABSENT_ID)).thenReturn(Optional.of(absent));
    }

    @Test
    void aNewRequestIsToldToTheSupervisorNamedOnIt() {
        notifier.submitted(waitingForSupervisor());

        verify(notifications).notifyUser(eq(SUPERVISOR_ACCOUNT), eq(NotificationKind.REQUEST_WAITING),
                contains("recommendation"), contains("Test Employee (EMP-001)"), isNull());
    }

    @Test
    void aRequestFromAHeadOfDepartmentIsToldToTheCentralAdministrators() {
        when(users.findEnabledWithRole(AppRole.CENTRAL_ADMIN)).thenReturn(List.of(centralAdmin));

        notifier.submitted(fromHeadOfDepartment());

        verify(notifications).notifyUser(eq(ADMIN_ACCOUNT), eq(NotificationKind.REQUEST_WAITING),
                contains("final decision"), anyString(), isNull());
    }

    /**
     * A person without an account, or with one that has been switched off, cannot
     * read a message, so nothing is written for them and the queue does not
     * quietly fill with unread mail nobody will ever see.
     */
    @Test
    void anAccountThatCannotSignInIsNotTold() {
        AppUser disabled = account(SUPERVISOR_ACCOUNT);
        when(disabled.isEnabled()).thenReturn(false);
        when(users.findByEmployeeId(SUPERVISOR_ID)).thenReturn(Optional.of(disabled));

        notifier.submitted(waitingForSupervisor());

        verifyNoInteractions(notifications);
    }

    @Test
    void aSupervisorsRecommendationIsToldToTheHeadsOfThatDepartment() {
        LeaveApplication recommended = waitingForDecision();
        recommended.recommend("Recommended");
        when(users.findEnabledWithRoleInDepartment(AppRole.HOD, DEPARTMENT_ID)).thenReturn(List.of(hod));

        notifier.supervisorDecided(recommended, supervisor, null);

        verify(notifications).notifyUser(eq(HOD_ACCOUNT), eq(NotificationKind.REQUEST_RECOMMENDED),
                contains("recommended"), contains("Test Employee (EMP-001)"), isNull());
    }

    @Test
    void aRecommendationReachesTheCentralAdministratorsWhenTheDepartmentHasNoHead() {
        LeaveApplication recommended = waitingForDecision();
        recommended.recommend("Recommended");
        when(users.findEnabledWithRoleInDepartment(AppRole.HOD, DEPARTMENT_ID)).thenReturn(List.of());
        when(users.findEnabledWithRole(AppRole.CENTRAL_ADMIN)).thenReturn(List.of(centralAdmin));

        notifier.supervisorDecided(recommended, supervisor, null);

        verify(notifications).notifyUser(eq(ADMIN_ACCOUNT), eq(NotificationKind.REQUEST_RECOMMENDED),
                anyString(), anyString(), isNull());
    }

    @Test
    void aRefusalAtTheSupervisorStepIsToldToTheApplicant() {
        LeaveApplication refused = waitingForSupervisor();
        refused.rejectBySupervisor("No cover available");

        notifier.supervisorDecided(refused, supervisor, null);

        verify(notifications).notifyUser(eq(APPLICANT_ACCOUNT), eq(NotificationKind.REQUEST_DECIDED),
                contains("not recommended"), contains("No cover available"), isNull());
    }

    @Test
    void theFinalAnswerIsToldToThePersonWhoAsked() {
        LeaveApplication approved = waitingForDecision();
        approved.decide(true, false, SUPERVISOR_ID, "Enjoy");

        notifier.decided(approved, supervisor, null);

        verify(notifications).notifyUser(eq(APPLICANT_ACCOUNT), eq(NotificationKind.REQUEST_DECIDED),
                eq("Your leave was approved"),
                contains("approved by Name of " + SUPERVISOR_ACCOUNT), isNull());
    }

    @Test
    void aCompensatoryApprovalSaysThatNoDaysWereTaken() {
        LeaveApplication compensated = waitingForDecision();
        compensated.decide(true, true, SUPERVISOR_ID, null);

        notifier.decided(compensated, supervisor, null);

        verify(notifications).notifyUser(eq(APPLICANT_ACCOUNT), eq(NotificationKind.REQUEST_DECIDED),
                contains("without deducting days"), contains("No days were taken from your balance."),
                isNull());
    }

    @Test
    void aRefusalFromTheFinalDecisionIsToldToTheApplicant() {
        LeaveApplication refused = waitingForDecision();
        refused.decide(false, false, SUPERVISOR_ID, "Peak period");

        notifier.decided(refused, supervisor, null);

        verify(notifications).notifyUser(eq(APPLICANT_ACCOUNT), eq(NotificationKind.REQUEST_DECIDED),
                contains("refused"), contains("Peak period"), isNull());
    }

    @Test
    void nobodyIsToldAboutAnActionTheyTookThemselves() {
        LeaveApplication own = waitingForDecision();
        own.decide(true, false, APPLICANT_ID, null);

        notifier.decided(own, applicant, null);

        verifyNoInteractions(notifications);
    }

    @Test
    void aWithdrawalIsToldToTheSupervisorWhoWasAsked() {
        LeaveApplication withdrawn = waitingForSupervisor();
        withdrawn.withdraw("Plans changed");

        notifier.withdrawn(withdrawn, LeaveStatus.PENDING_SUPERVISOR, applicant);

        verify(notifications).notifyUser(eq(SUPERVISOR_ACCOUNT), eq(NotificationKind.REQUEST_WITHDRAWN),
                contains("withdrawn"), anyString(), isNull());
    }

    @Test
    void aWithdrawalPastTheSupervisorStepIsToldToTheDeciders() {
        LeaveApplication withdrawn = waitingForDecision();
        withdrawn.withdraw("Plans changed");
        when(users.findEnabledWithRoleInDepartment(AppRole.HOD, DEPARTMENT_ID)).thenReturn(List.of(hod));

        notifier.withdrawn(withdrawn, LeaveStatus.PENDING, applicant);

        verify(notifications).notifyUser(eq(HOD_ACCOUNT), eq(NotificationKind.REQUEST_WITHDRAWN),
                contains("withdrawn"), anyString(), isNull());
    }

    /**
     * The words a message is made of. A date written the way the rest of the
     * platform writes dates is what makes a message recognisable in a list, and
     * the leave type and the day count are what make it actionable.
     */
    @Test
    void theWordsNameWhoAskedForHowManyDaysAndOverWhichDates() {
        LeaveApplication application = waitingForSupervisor();

        assertThat(LeaveNotifier.span(application))
                .isEqualTo("3 day(s) of " + LeaveType.CASUAL + " leave from 2 Nov 2026 to 4 Nov 2026");
        assertThat(LeaveNotifier.requested(applicantEmployee, application)).isEqualTo(
                "Test Employee (EMP-001) asked for 3 day(s) of " + LeaveType.CASUAL
                        + " leave from 2 Nov 2026 to 4 Nov 2026.");
    }

    @Test
    void aRequestNobodyIsAskedAboutLeavesTheBellEmpty() {
        when(users.findByEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(users.findEnabledWithRole(anyString())).thenReturn(List.of());

        notifier.submitted(waitingForSupervisor());

        verifyNoInteractions(notifications);
    }


    /**
     * The promise a delegation makes to the person who handed their approving
     * over: whatever the colleague did while you were away, you hear about it.
     * The message carries the delegation number, which is why the id argument is
     * not null here as it is for a leave message - a click opens the person's
     * own delegations screen, not the request.
     */
    @Test
    void aStepTakenUnderAWindowIsToldToThePersonWhoseApprovingItWas() {
        ApprovalDelegation held = windowOver(ABSENT_ID, ABSENT_ACCOUNT);
        LeaveApplication recommended = waitingForDecision();
        recommended.recommend("Recommended");
        when(users.findEnabledWithRoleInDepartment(AppRole.HOD, DEPARTMENT_ID)).thenReturn(List.of(hod));

        notifier.supervisorDecided(recommended, supervisor, held);

        verify(notifications).notifyUser(eq(ABSENT_ACCOUNT), eq(NotificationKind.DELEGATION_ACTED),
                contains("your delegation"),
                contains("Name of supervisor.one acted for you"), eq(500L));
    }

    @Test
    void theFinalDecisionUnderAWindowReachesTheDelegatorToo() {
        ApprovalDelegation held = windowOver(ABSENT_ID, ABSENT_ACCOUNT);
        LeaveApplication approved = waitingForDecision();
        approved.decide(true, false, SUPERVISOR_ID, "Enjoy");

        notifier.decided(approved, supervisor, held);

        verify(notifications).notifyUser(eq(ABSENT_ACCOUNT), eq(NotificationKind.DELEGATION_ACTED),
                anyString(), contains("acted for you"), eq(500L));
        verify(notifications).notifyUser(eq(APPLICANT_ACCOUNT), eq(NotificationKind.REQUEST_DECIDED),
                anyString(), anyString(), isNull());
    }

    @Test
    void aSupervisorActingInTheirOwnQueueHearsNothingFromThemself() {
        ApprovalDelegation held = windowOver(SUPERVISOR_ID, SUPERVISOR_ACCOUNT);
        LeaveApplication approved = waitingForDecision();
        approved.decide(true, false, SUPERVISOR_ID, "Enjoy");

        notifier.decided(approved, supervisor, held);

        // Only the applicant's copy is written; the actor would be messaging
        // their own name back at them.
        verify(notifications).notifyUser(eq(APPLICANT_ACCOUNT), eq(NotificationKind.REQUEST_DECIDED),
                anyString(), anyString(), isNull());
        org.mockito.Mockito.verifyNoMoreInteractions(notifications);
    }

    private static ApprovalDelegation windowOver(Long delegatorId, String delegatorAccount) {
        Employee delegator = mock(Employee.class);
        when(delegator.getId()).thenReturn(delegatorId);
        when(delegator.getFullName()).thenReturn("Name of " + delegatorAccount);
        ApprovalDelegation held = new ApprovalDelegation(delegator, mock(Employee.class),
                DelegationScope.SUPERVISOR, null, MONDAY, WEDNESDAY, null);
        try {
            var field = ApprovalDelegation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(held, 500L);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        held.stampReference();
        return held;
    }

    private LeaveApplication waitingForSupervisor() {
        return new LeaveApplication(applicantEmployee, type, MONDAY, WEDNESDAY, 3, "Family event",
                LeaveStatus.PENDING_SUPERVISOR, SUPERVISOR_ID);
    }

    private LeaveApplication waitingForDecision() {
        return new LeaveApplication(applicantEmployee, type, MONDAY, WEDNESDAY, 3, "Family event",
                LeaveStatus.PENDING, SUPERVISOR_ID);
    }

    /** A head of department has nobody above them to recommend, so the request starts here. */
    private LeaveApplication fromHeadOfDepartment() {
        return new LeaveApplication(applicantEmployee, type, MONDAY, WEDNESDAY, 3, "Family event",
                LeaveStatus.PENDING, null);
    }

    private static AppUser account(String username) {
        AppUser user = mock(AppUser.class);
        Employee person = mock(Employee.class);
        when(user.getUsername()).thenReturn(username);
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmployee()).thenReturn(person);
        when(person.getFullName()).thenReturn("Name of " + username);
        return user;
    }
}
