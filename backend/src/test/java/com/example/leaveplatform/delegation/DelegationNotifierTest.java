package com.example.leaveplatform.delegation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.notification.NotificationKind;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.organization.Department;

/**
 * Who hears about a handed-over window. Both sides always do - the delegatee
 * because the approving is now theirs to notice, the delegator because a window
 * closing under their name is the sort of thing one learns the hard way
 * otherwise - except when they are the one who did it.
 */
class DelegationNotifierTest {

    private static final Long DELEGATOR_ID = 1L;
    private static final Long DELEGATEE_ID = 2L;
    private static final String DELEGATOR = "hod.ce";
    private static final String DELEGATEE = "ali.ahmed";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final DelegationNotifier notifier = new DelegationNotifier(users, notifications);

    private final Employee delegator = person(DELEGATOR_ID, "Dr Shapeen Ullah");
    private final Employee delegatee = person(DELEGATEE_ID, "Ali Ahmed");
    private final Department department = mock(Department.class);

    @BeforeEach
    void commonStubs() {
        when(department.getName()).thenReturn("Computer and Information Systems Engineering");
        AppUser delegatorAccount = account(DELEGATOR, delegator);
        AppUser delegateeAccount = account(DELEGATEE, delegatee);
        when(users.findByEmployeeId(DELEGATOR_ID)).thenReturn(Optional.of(delegatorAccount));
        when(users.findByEmployeeId(DELEGATEE_ID)).thenReturn(Optional.of(delegateeAccount));
    }

    private ApprovalDelegation window() {
        ApprovalDelegation delegation = new ApprovalDelegation(delegator, delegatee,
                DelegationScope.DECISION, department,
                LocalDate.now(), LocalDate.now().plusDays(5), "I am at a conference.");
        setId(delegation, 9L);
        delegation.stampReference();
        return delegation;
    }

    @Test
    void theColleagueIsToldWhatTheyCanNowDoAndWhy() {
        notifier.offered(window(), account(DELEGATOR, delegator));

        verify(notifications).notifyUser(eq(DELEGATEE), eq(NotificationKind.DELEGATION_OFFERED),
                contains("Dr Shapeen Ullah"),
                org.mockito.ArgumentMatchers.argThat(body -> body != null
                        && body.contains("Final decision")
                        && body.contains("Computer and Information Systems Engineering")
                        && body.contains("conference")),
                eq(9L));
    }

    @Test
    void closingAWindowTellsTheHolderAndTheDelegatorWhenTheOfficeDidIt() {
        ApprovalDelegation delegation = window();
        AppUser officer = account("admin", mock(Employee.class));

        notifier.revoked(delegation, officer);

        verify(notifications).notifyUser(eq(DELEGATEE), eq(NotificationKind.DELEGATION_REVOKED),
                anyString(), contains("back with them from today"), eq(9L));
        verify(notifications).notifyUser(eq(DELEGATOR), eq(NotificationKind.DELEGATION_ENDED),
                anyString(), contains("administration office"), eq(9L));
    }

    @Test
    void aDelegatorWhoTookTheirApprovingBackHearsNothing() {
        ApprovalDelegation delegation = window();
        delegation.revoke(delegator);

        notifier.revoked(delegation, account(DELEGATOR, delegator));

        verify(notifications).notifyUser(eq(DELEGATEE), eq(NotificationKind.DELEGATION_REVOKED),
                anyString(), anyString(), eq(9L));
        verifyNoInteractionsOnTheDelegator();
    }

    private void verifyNoInteractionsOnTheDelegator() {
        verify(notifications, org.mockito.Mockito.never())
                .notifyUser(eq(DELEGATOR), org.mockito.ArgumentMatchers.any(),
                        anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    private static Employee person(Long id, String name) {
        Employee employee = mock(Employee.class);
        when(employee.getId()).thenReturn(id);
        when(employee.getFullName()).thenReturn(name);
        return employee;
    }

    private static AppUser account(String username, Employee person) {
        AppUser user = mock(AppUser.class);
        when(user.getUsername()).thenReturn(username);
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmployee()).thenReturn(person);
        return user;
    }

    private void setId(ApprovalDelegation delegation, long id) {
        try {
            var field = ApprovalDelegation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(delegation, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
