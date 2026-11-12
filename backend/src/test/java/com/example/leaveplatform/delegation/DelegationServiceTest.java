package com.example.leaveplatform.delegation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.organization.Department;

/**
 * Who may hand which approving to whom, and for which days.
 *
 * <p>The cases are the ones a friendly screen would not show but a hostile or
 * mistaken caller will: a window onto a colleague's authority rather than
 * one's own, a date in the past, an overlapping second window, a delegatee
 * without an account, a head-of-department window asked for by someone who
 * heads nothing, and a revoke from the wrong pair of hands. The promise the
 * whole feature rests on - the delegatee acts as themself and the platform says
 * so - is checked on the audit detail and on the notice to the delegator.
 */
class DelegationServiceTest {

    private static final String HOD = "hod.ce";
    private static final String SUPERVISOR = "supervisor.ce";
    private static final String COLLEAGUE = "ali.ahmed";
    private static final String DESK = "admin";
    private static final long HOD_ID = 1L;
    private static final long SUPERVISOR_ID = 2L;
    private static final long COLLEAGUE_ID = 3L;
    private static final long DESK_ID = 4L;

    // Anchored to the real clock: a fixed "today" would let the window slide
    // into the past at the next midnight and fail for the wrong reason.
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate FROM = TODAY;
    private static final LocalDate TO = TODAY.plusDays(9);

    private final ApprovalDelegationRepository delegations = mock(ApprovalDelegationRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final DelegationNotifier notifier = mock(DelegationNotifier.class);

    private final Department department = mock(Department.class);
    private AppUser deskAccount;
    private final Employee hod = mock(Employee.class);
    private final Employee supervisor = mock(Employee.class);
    private final Employee colleague = mock(Employee.class);
    private final Employee deskMember = mock(Employee.class);

    private final DelegationService service =
            new DelegationService(delegations, employees, users, audit, notifier);

    @BeforeEach
    void commonStubs() {
        when(department.getId()).thenReturn(10L);
        when(department.getName()).thenReturn("Computer and Information Systems Engineering");
        holds(hod, HOD_ID, "Dr Shapeen Ullah", "CS-101");
        holds(supervisor, SUPERVISOR_ID, "Ms Sadia Khan", "CS-102");
        holds(colleague, COLLEAGUE_ID, "Ali Ahmed", "CS-103");
        holds(deskMember, DESK_ID, "Nadia Ahmed", "ADMIN-002");

        AppUser hodAccount = account(HOD, hod, AppRole.HOD);
        AppUser supervisorAccount = account(SUPERVISOR, supervisor, AppRole.SUPERVISOR);
        AppUser colleagueAccount = account(COLLEAGUE, colleague, "EMPLOYEE");
        deskAccount = account(DESK, deskMember, AppRole.CENTRAL_ADMIN);
        when(users.findByUsername(HOD)).thenReturn(Optional.of(hodAccount));
        when(users.findByUsername(SUPERVISOR)).thenReturn(Optional.of(supervisorAccount));
        when(users.findByUsername(COLLEAGUE)).thenReturn(Optional.of(colleagueAccount));
        when(users.findByUsername(DESK)).thenReturn(Optional.of(deskAccount));
        when(users.findByEmployeeId(COLLEAGUE_ID)).thenReturn(Optional.of(colleagueAccount));

        when(employees.findByIdAndActiveTrue(COLLEAGUE_ID)).thenReturn(Optional.of(colleague));
        when(employees.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());
        when(employees.findByIdForUpdate(HOD_ID)).thenReturn(Optional.of(hod));
        when(employees.findByIdForUpdate(SUPERVISOR_ID)).thenReturn(Optional.of(supervisor));
        when(employees.existsBySupervisorEmployeeIdAndActiveTrue(SUPERVISOR_ID)).thenReturn(true);
        when(employees.existsBySupervisorEmployeeIdAndActiveTrue(HOD_ID)).thenReturn(false);

        when(delegations.findHeld(anyLong())).thenReturn(List.of());
        when(delegations.save(any())).thenAnswer(call -> {
            ApprovalDelegation saved = call.getArgument(0);
            setId(saved, 7L);
            return saved;
        });
    }

    private void holds(Employee person, long id, String name, String number) {
        when(person.getId()).thenReturn(id);
        when(person.getFullName()).thenReturn(name);
        when(person.getEmployeeNo()).thenReturn(number);
        when(person.getDepartment()).thenReturn(department);
        when(person.isActive()).thenReturn(true);
    }

    private AppUser account(String username, Employee person, String roleCode) {
        AppUser user = mock(AppUser.class);
        AppRole role = mock(AppRole.class);
        when(role.getCode()).thenReturn(roleCode);
        when(user.getUsername()).thenReturn(username);
        when(user.getEmployee()).thenReturn(person);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(user.isEnabled()).thenReturn(true);
        return user;
    }

    private NewDelegationRequest window(Long to, DelegationScope scope, LocalDate from, LocalDate to2) {
        return new NewDelegationRequest(to, scope, from, to2, "I am on leave.");
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

    @Test
    void aSupervisorWindowIsNumberedAuditedAndOfferedToTheColleague() {
        DelegationResponse response =
                service.create(SUPERVISOR, window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, FROM, TO));

        ArgumentCaptor<ApprovalDelegation> saved = ArgumentCaptor.forClass(ApprovalDelegation.class);
        verify(delegations).save(saved.capture());
        assertThat(saved.getValue().getScope()).isEqualTo(DelegationScope.SUPERVISOR);
        assertThat(saved.getValue().getDepartment()).isNull();
        assertThat(saved.getValue().covers(TODAY)).isTrue();
        assertThat(saved.getValue().covers(TO)).isTrue();
        assertThat(saved.getValue().covers(TO.plusDays(1))).isFalse();
        assertThat(response.reference()).isEqualTo("DL-000007");
        assertThat(response.mine()).isTrue();

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).recordAs(org.mockito.ArgumentMatchers.eq(SUPERVISOR),
                org.mockito.ArgumentMatchers.eq(AuditAction.DELEGATION_CREATED),
                org.mockito.ArgumentMatchers.eq("APPROVAL_DELEGATION"), any(), detail.capture());
        assertThat(detail.getValue()).contains("Ali Ahmed").contains("CS-103")
                .contains(FROM.toString()).contains(TO.toString());
        verify(notifier).offered(any(), any());
    }

    @Test
    void aDecisionWindowSnapshotsTheDelegatorsDepartment() {
        service.create(HOD, window(COLLEAGUE_ID, DelegationScope.DECISION, FROM, TO));

        ArgumentCaptor<ApprovalDelegation> saved = ArgumentCaptor.forClass(ApprovalDelegation.class);
        verify(delegations).save(saved.capture());
        assertThat(saved.getValue().getScope()).isEqualTo(DelegationScope.DECISION);
        assertThat(saved.getValue().getDepartment()).isSameAs(department);
    }

    @Test
    void nobodyHandsOverTheirOwnApprovingToThemself() {
        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(SUPERVISOR_ID, DelegationScope.SUPERVISOR, FROM, TO)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("yourself");
        verify(delegations, never()).save(any());
    }

    @Test
    void aWindowCannotStartInThePast() {
        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, TODAY.minusDays(1), TO)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("past");
        verify(delegations, never()).save(any());
    }

    @Test
    void theLastDayCannotBeBeforeTheFirst() {
        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, TO, FROM)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("cannot be before");
    }

    @Test
    void aWindowCannotReachBeyondAYearAhead() {
        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, FROM, TODAY.plusDays(400))))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("366");
    }

    @Test
    void aColleagueWhoHasLeftCannotBeGivenAWindow() {
        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(999L, DelegationScope.SUPERVISOR, FROM, TO)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("not on the active staff");
    }

    @Test
    void aColleagueWithoutAnAccountWouldNeverSeeTheOffer() {
        when(users.findByEmployeeId(COLLEAGUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, FROM, TO)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("sign-in account");
    }

    @Test
    void aSupervisorWindowNeedsSomeoneWhoActuallySupervises() {
        assertThatThrownBy(() -> service.create(HOD,
                window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, FROM, TO)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("reports to you");
    }

    @Test
    void aDecisionWindowNeedsTheHeadOfADepartment() {
        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(COLLEAGUE_ID, DelegationScope.DECISION, FROM, TO)))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("head of department");
    }

    @Test
    void aSecondWindowOverTheSameWeekIsRefusedByName() {
        ApprovalDelegation existing =
                new ApprovalDelegation(supervisor, colleague, DelegationScope.SUPERVISOR,
                        null, FROM, TO, "I am on leave.");
        setId(existing, 3L);
        existing.stampReference();
        when(delegations.findHeld(SUPERVISOR_ID)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(SUPERVISOR,
                window(COLLEAGUE_ID, DelegationScope.SUPERVISOR, FROM.plusDays(2), TO.plusDays(2))))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("DL-000003").hasMessageContaining("Ali Ahmed");
        verify(delegations, never()).save(any());
    }

    @Test
    void aWindowForNextYearSitsBesideThisYearsWithoutColliding() {
        ApprovalDelegation existing =
                new ApprovalDelegation(supervisor, colleague, DelegationScope.SUPERVISOR,
                        null, FROM, TO, "Last year.");
        setId(existing, 3L);
        existing.stampReference();
        when(delegations.findHeld(SUPERVISOR_ID)).thenReturn(List.of(existing));

        // The far window starts the day after the first ends, kept within the
        // horizon a hand-over may reach: the point is that a later window
        // beside an earlier one is not a collision, not where the calendar year falls.
        service.create(SUPERVISOR, window(COLLEAGUE_ID, DelegationScope.SUPERVISOR,
                TODAY.plusDays(361), TODAY.plusDays(364)));

        verify(delegations).save(any());
    }

    @Test
    void aDelegateeCannotGiveTheirWindowBack() {
        ApprovalDelegation held =
                new ApprovalDelegation(supervisor, colleague, DelegationScope.SUPERVISOR,
                        null, FROM, TO, null);
        setId(held, 8L);
        held.stampReference();
        when(delegations.findByIdForUpdate(8L)).thenReturn(Optional.of(held));

        assertThatThrownBy(() -> service.revoke(COLLEAGUE, 8L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only Ms Sadia Khan");
        assertThat(held.isActive()).isTrue();
    }

    @Test
    void theOfficeCanCloseAWindowTheDelegatorForgot() {
        ApprovalDelegation held =
                new ApprovalDelegation(supervisor, colleague, DelegationScope.SUPERVISOR,
                        null, FROM, TO, null);
        setId(held, 8L);
        held.stampReference();
        when(delegations.findByIdForUpdate(8L)).thenReturn(Optional.of(held));

        DelegationResponse response = service.revoke(DESK, 8L);

        assertThat(held.isActive()).isFalse();
        assertThat(held.covers(FROM)).isFalse();
        assertThat(response.state()).isEqualTo("Revoked");
        verify(audit).recordAs(org.mockito.ArgumentMatchers.eq(DESK),
                org.mockito.ArgumentMatchers.eq(AuditAction.DELEGATION_REVOKED), any(), any(), any());
        verify(notifier).revoked(held, deskAccount);
    }

    @Test
    void aWindowAlreadyClosedIsNotClosedTwice() {
        ApprovalDelegation held =
                new ApprovalDelegation(supervisor, colleague, DelegationScope.SUPERVISOR,
                        null, FROM, TO, null);
        held.revoke(deskMember);
        when(delegations.findByIdForUpdate(8L)).thenReturn(Optional.of(held));

        assertThatThrownBy(() -> service.revoke(SUPERVISOR, 8L))
                .isInstanceOf(DelegationRuleException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void theWholeRegisterIsTheOfficesToReadAndNobodyElses() {
        when(delegations.findLatest(any(Pageable.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.all(SUPERVISOR))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        service.all(DESK);
    }

    @Test
    void theActingForWordsOnlyAppearUnderAWindow() {
        ApprovalDelegation held =
                new ApprovalDelegation(supervisor, colleague, DelegationScope.SUPERVISOR,
                        null, FROM, TO, null);
        setId(held, 21L);
        held.stampReference();

        assertThat(DelegationService.actingFor(null)).isEmpty();
        assertThat(DelegationService.actingFor(held))
                .isEqualTo(" (acting for Ms Sadia Khan, DL-000021)");
    }
}
