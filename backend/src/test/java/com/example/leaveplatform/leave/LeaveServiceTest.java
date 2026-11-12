package com.example.leaveplatform.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.delegation.ApprovalDelegation;
import com.example.leaveplatform.delegation.DelegationScope;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.organization.Department;

class LeaveServiceTest {

    private static final String USERNAME = "employee.one";
    private static final Long EMPLOYEE_ID = 10L;
    private static final Long TYPE_ID = 5L;
    private static final int YEAR = 2026;
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 3);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final com.example.leaveplatform.identity.EmployeeRepository employees =
            mock(com.example.leaveplatform.identity.EmployeeRepository.class);
    private final LeaveApplicationRepository applications = mock(LeaveApplicationRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    private final PublicHolidayRepository holidays = mock(PublicHolidayRepository.class);
    private final com.example.leaveplatform.audit.AuditService audit = mock(com.example.leaveplatform.audit.AuditService.class);
    private final LeaveNotifier notifier = mock(LeaveNotifier.class);
    private final com.example.leaveplatform.delegation.DelegationService delegations =
            mock(com.example.leaveplatform.delegation.DelegationService.class);
    private final LeaveService service = new LeaveService(users, employees, applications, balances, types,
            holidays, audit, notifier, delegations);

    private final Department department = mock(Department.class);
    private final Employee employee = mock(Employee.class);
    private final LeaveType type = mock(LeaveType.class);

    @BeforeEach
    void commonStubs() {
        when(department.getId()).thenReturn(1L);
        when(department.getName()).thenReturn("Computer and Information Systems Engineering");
        when(employee.getId()).thenReturn(EMPLOYEE_ID);
        when(employee.getEmployeeNo()).thenReturn("EMP-001");
        when(employee.getFullName()).thenReturn("Test Employee");
        when(employee.getDepartment()).thenReturn(department);
        when(employee.getSupervisorEmployeeId()).thenReturn(null);
        when(type.getId()).thenReturn(TYPE_ID);
        when(type.getCode()).thenReturn(LeaveType.CASUAL);
        when(type.getName()).thenReturn("Casual Leave");
        when(type.getAnnualAllowance()).thenReturn(25);
        when(types.findByCodeAndActiveTrue(LeaveType.CASUAL)).thenReturn(Optional.of(type));
        when(holidays.findDatesBetween(any(), any())).thenReturn(List.of());
        when(applications.save(any())).thenAnswer(call -> {
            LeaveApplication saved = call.getArgument(0);
            // The insert trigger is what writes the number, and a mock save has
            // no trigger, so the row gets the id one would have been given.
            setId(saved, 7L);
            return saved;
        });
        when(delegations.supervisorHolds(any())).thenReturn(List.of());
        when(delegations.decisionHolds(any())).thenReturn(List.of());
    }

    @Test
    void submissionGoesToTheSupervisorFirst() {
        when(employee.getSupervisorEmployeeId()).thenReturn(20L);
        signIn("EMPLOYEE");

        LeaveApplicationResponse response =
                service.submit(USERNAME, new SubmitLeaveRequest(MONDAY, WEDNESDAY, "Family event"));

        ArgumentCaptor<LeaveApplication> saved = ArgumentCaptor.forClass(LeaveApplication.class);
        verify(applications).save(saved.capture());
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING_SUPERVISOR);
        assertThat(response.days()).isEqualTo(3);
        // The applicant is told the number the paper will carry, so they can
        // quote it before anybody has printed anything.
        assertThat(response.reference()).isEqualTo("LV-000007");
        assertThat(saved.getValue().getSupervisorEmployeeId()).isEqualTo(20L);
    }

    @Test
    void hodSubmissionSkipsTheSupervisorStep() {
        when(employee.getSupervisorEmployeeId()).thenReturn(20L);
        signIn("HOD");

        LeaveApplicationResponse response =
                service.submit(USERNAME, new SubmitLeaveRequest(MONDAY, WEDNESDAY, "Family event"));

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        ArgumentCaptor<LeaveApplication> saved = ArgumentCaptor.forClass(LeaveApplication.class);
        verify(applications).save(saved.capture());
        assertThat(saved.getValue().getSupervisorEmployeeId()).isNull();
    }

    @Test
    void submissionWithoutSupervisorGoesStraightToTheHod() {
        signIn("EMPLOYEE");

        LeaveApplicationResponse response =
                service.submit(USERNAME, new SubmitLeaveRequest(MONDAY, WEDNESDAY, "Family event"));

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void serverCountsTheDaysAndSkipsWeekdayHolidays() {
        when(holidays.findDatesBetween(MONDAY, WEDNESDAY)).thenReturn(List.of(TUESDAY));
        signIn("EMPLOYEE");

        LeaveApplicationResponse response =
                service.submit(USERNAME, new SubmitLeaveRequest(MONDAY, WEDNESDAY, null));

        assertThat(response.days()).isEqualTo(2);
    }

    @Test
    void holidayOnlyRequestIsRefused() {
        when(holidays.findDatesBetween(MONDAY, TUESDAY)).thenReturn(List.of(MONDAY, TUESDAY));
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.submit(USERNAME, new SubmitLeaveRequest(MONDAY, TUESDAY, null)))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("public holidays");
        verify(applications, never()).save(any());
    }

    @Test
    void overlappingRequestIsRefused() {
        when(applications.existsOverlapping(eq(EMPLOYEE_ID), eq(MONDAY), eq(WEDNESDAY), any()))
                .thenReturn(true);
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.submit(USERNAME, new SubmitLeaveRequest(MONDAY, WEDNESDAY, null)))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("overlaps");
        verify(applications, never()).save(any());
    }

    @Test
    void endDateBeforeStartDateIsRefused() {
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.submit(USERNAME, new SubmitLeaveRequest(WEDNESDAY, MONDAY, null)))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void leaveThatCrossesIntoTheNextYearIsRefusedWithTheTwoDatesToApplyFor() {
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.submit(USERNAME, new SubmitLeaveRequest(
                LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 4), "New year")))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("2027")
                // The refusal has to be actionable: it names both halves, so the
                // applicant can retype them instead of guessing where to cut.
                .hasMessageContaining("2026-12-28 to 2026-12-31")
                .hasMessageContaining("2027-01-01 to 2027-01-04");
        verify(applications, never()).save(any());
    }

    @Test
    void aRequestThatEndsOnTheLastDayOfTheYearIsStillOneRequest() {
        signIn("EMPLOYEE");

        LeaveApplicationResponse response = service.submit(USERNAME, new SubmitLeaveRequest(
                LocalDate.of(2026, 12, 28), LocalDate.of(2026, 12, 31), null));

        assertThat(response.days()).isEqualTo(4);
    }

    @Test
    void aRequestThatStartsOnTheFirstDayOfTheYearIsStillOneRequest() {
        signIn("EMPLOYEE");

        LeaveApplicationResponse response = service.submit(USERNAME, new SubmitLeaveRequest(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 4), null));

        assertThat(response.days()).isEqualTo(4);
    }

    @Test
    void onlyTheNamedSupervisorMayRecommend() {
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x",
                        LeaveStatus.PENDING_SUPERVISOR, 99L);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.supervisorDecision(USERNAME, 7L, true, "ok"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING_SUPERVISOR);
    }

    @Test
    void supervisorRecommendationMovesTheRequestToTheHod() {
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x",
                        LeaveStatus.PENDING_SUPERVISOR, EMPLOYEE_ID);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("SUPERVISOR");

        service.supervisorDecision(USERNAME, 7L, true, "  fine  ");

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(application.getRemarks()).isEqualTo("fine");
        assertThat(application.getSupervisorDecidedAt()).isNotNull();
    }

    @Test
    void supervisorRejectionEndsTheRequest() {
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x",
                        LeaveStatus.PENDING_SUPERVISOR, EMPLOYEE_ID);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("SUPERVISOR");

        service.supervisorDecision(USERNAME, 7L, false, "Team is short");

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    void approvalDeductsTheDays() {
        LeaveApplication application = pendingApplication(employee, 3);
        LeaveBalance balance = new LeaveBalance(employee, type, YEAR, 25);
        balance.use(1);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.of(balance));
        signIn("HOD");

        service.decide(USERNAME, 7L, true, false, "Approved");

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(application.getDecidedByEmployeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(balance.getUsed()).isEqualTo(4);
        assertThat(balance.remaining()).isEqualTo(21);
    }

    @Test
    void approvalIsRefusedWhenTheBalanceIsShort() {
        LeaveApplication application = pendingApplication(employee, 3);
        LeaveBalance balance = new LeaveBalance(employee, type, YEAR, 2);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.of(balance));
        signIn("HOD");

        assertThatThrownBy(() -> service.decide(USERNAME, 7L, true, false, null))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("remaining balance is 2");
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(balance.getUsed()).isZero();
    }

    @Test
    void theBalanceIsCreatedFromTheTypeOnFirstApproval() {
        LeaveApplication application = pendingApplication(employee, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.empty());
        when(balances.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        signIn("HOD");

        service.decide(USERNAME, 7L, true, false, null);

        ArgumentCaptor<LeaveBalance> created = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(balances).saveAndFlush(created.capture());
        assertThat(created.getValue().getAllocated()).isEqualTo(25);
        assertThat(created.getValue().getUsed()).isEqualTo(3);
    }

    @Test
    void theEmployeeRowIsLockedBeforeABalanceThatDoesNotExistYetIsCreated() {
        LeaveApplication application = pendingApplication(employee, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.empty());
        when(balances.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        signIn("HOD");

        service.decide(USERNAME, 7L, true, false, null);

        // Two decisions on the same person's first request of the year would
        // otherwise both find no balance row and both try to insert one, and the
        // loser would fail on the unique constraint.
        InOrder order = inOrder(applications, employees, balances);
        order.verify(applications).findByIdForUpdate(7L);
        order.verify(employees).findByIdForUpdate(EMPLOYEE_ID);
        order.verify(balances).findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR);
        order.verify(balances).saveAndFlush(any(LeaveBalance.class));
    }

    @Test
    void hodFromAnotherDepartmentIsRefused() {
        Employee other = mock(Employee.class);
        Department otherDepartment = mock(Department.class);
        when(otherDepartment.getId()).thenReturn(2L);
        when(other.getId()).thenReturn(20L);
        when(other.getDepartment()).thenReturn(otherDepartment);
        LeaveApplication application = pendingApplication(other, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("HOD");

        assertThatThrownBy(() -> service.decide(USERNAME, 7L, true, false, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void centralAdminDecidesAnyDepartment() {
        Employee other = mock(Employee.class);
        Department otherDepartment = mock(Department.class);
        when(otherDepartment.getId()).thenReturn(2L);
        when(other.getId()).thenReturn(20L);
        when(other.getDepartment()).thenReturn(otherDepartment);
        LeaveApplication application = pendingApplication(other, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(20L, TYPE_ID, YEAR))
                .thenReturn(Optional.of(new LeaveBalance(other, type, YEAR, 25)));
        signIn("CENTRAL_ADMIN");

        service.decide(USERNAME, 7L, true, false, null);

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void aRequestThatWasAlreadyDecidedCannotBeDecidedAgain() {
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x", LeaveStatus.APPROVED, null);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("HOD");

        assertThatThrownBy(() -> service.decide(USERNAME, 7L, false, false, null))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("no longer waiting");
    }

    @Test
    void compensatoryApprovalGrantsTheLeaveWithoutSpendingDays() {
        LeaveApplication application = pendingApplication(employee, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("HOD");

        service.decide(USERNAME, 7L, true, true, "Duty on the weekend");

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(application.isCompensated()).isTrue();
        verify(balances, never()).findForUpdate(any(), any(), anyInt());
    }

    @Test
    void aNormalApprovalIsNotMarkedCompensatory() {
        LeaveApplication application = pendingApplication(employee, 3);
        LeaveBalance balance = new LeaveBalance(employee, type, YEAR, 25);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.of(balance));
        signIn("HOD");

        service.decide(USERNAME, 7L, true, false, "Approved");

        assertThat(application.isCompensated()).isFalse();
        assertThat(balance.getUsed()).isEqualTo(3);
    }

    @Test
    void compensatoryCannotBeUsedToReject() {
        LeaveApplication application = pendingApplication(employee, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("HOD");

        assertThatThrownBy(() -> service.decide(USERNAME, 7L, false, true, null))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("only applies when approving");
    }

    @Test
    void theApplicantWithdrawsARequestThatIsStillWaiting() {
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x",
                        LeaveStatus.PENDING_SUPERVISOR, 20L);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("EMPLOYEE");

        LeaveApplicationResponse response = service.withdraw(USERNAME, 7L,  "  Planned to attend  ");

        assertThat(response.status()).isEqualTo(LeaveStatus.WITHDRAWN);
        assertThat(application.getRemarks()).isEqualTo("Planned to attend");
        assertThat(application.getWithdrawnAt()).isNotNull();
        verify(audit).recordAs(eq(USERNAME), eq(com.example.leaveplatform.audit.AuditAction.LEAVE_WITHDRAWN),
                eq("LEAVE_APPLICATION"), eq(7L), any());
    }

    @Test
    void aRequestThatWasAlreadyDecidedCannotBeWithdrawn() {
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x", LeaveStatus.APPROVED, null);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.withdraw(USERNAME, 7L, null))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("already been decided");
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void onlyTheApplicantMayWithdrawTheRequest() {
        Employee other = mock(Employee.class);
        when(other.getId()).thenReturn(20L);
        LeaveApplication application =
                new LeaveApplication(other, type, MONDAY, WEDNESDAY, 3, "x", LeaveStatus.PENDING, null);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.withdraw(USERNAME, 7L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void balanceComesFromTheTypeUntilTheFirstUse() {
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, TYPE_ID,
                LocalDate.now().getYear())).thenReturn(Optional.empty());
        signIn("EMPLOYEE");

        LeaveBalanceResponse response = service.balance(USERNAME);

        assertThat(response.allocated()).isEqualTo(25);
        assertThat(response.used()).isZero();
        assertThat(response.remaining()).isEqualTo(25);
    }

    @Test
    void theApplicantListCarriesTheChosenPeriodAndStatusToTheDatabase() {
        when(applications.findAll(anyQuery(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingApplication(employee, 3))));
        signIn("EMPLOYEE");

        List<LeaveApplicationResponse> found =
                service.myApplications(USERNAME, new LeaveFilter(MONDAY, WEDNESDAY, LeaveStatus.PENDING));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(applications).findAll(anyQuery(), page.capture());
        assertThat(found).hasSize(1);
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(LeaveService.LIST_LIMIT);
        assertThat(page.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void theApprovalQueuesReadTheOldestWaitingRequestsFirst() {
        when(applications.findAll(anyQuery(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        signIn("HOD");

        ApprovalQueueResponse queues = service.approvals(USERNAME, new LeaveFilter(MONDAY, null, null));

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(applications, times(2)).findAll(anyQuery(), pages.capture());
        assertThat(queues.supervisorQueue()).isEmpty();
        assertThat(queues.decisionQueue()).isEmpty();
        pages.getAllValues().forEach(page -> assertThat(page.getSort()).isEqualTo(
                Sort.by(Sort.Direction.ASC, "startDate").and(Sort.by(Sort.Direction.ASC, "id"))));
    }

    @Test
    void anEmployeeAsksOnlyForTheirOwnSupervisorQueue() {
        when(applications.findAll(anyQuery(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        signIn("EMPLOYEE");

        service.approvals(USERNAME, LeaveFilter.any());

        verify(applications, times(1)).findAll(anyQuery(), any(Pageable.class));
    }

    @Test
    void aCentralAdministratorSeesTheWholeUniversityQueue() {
        when(applications.findAll(anyQuery(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingApplication(employee, 3))));
        signIn("CENTRAL_ADMIN");

        ApprovalQueueResponse queues = service.approvals(USERNAME, LeaveFilter.any());

        assertThat(queues.decisionQueue()).hasSize(1);
        assertThat(queues.decisionQueue().get(0).status()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void aRequestOutsideTheChosenPeriodIsLeftOutByTheDatabaseQuery() {
        // The filter itself becomes part of the query, so an empty result means
        // the server asked for nothing else - see LeaveApplicationSpecifications.
        when(applications.findAll(anyQuery(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        signIn("EMPLOYEE");

        List<LeaveApplicationResponse> found =
                service.myApplications(USERNAME, new LeaveFilter(MONDAY.plusYears(1), null, null));

        assertThat(found).isEmpty();
        verify(applications).findAll(anyQuery(), any(Pageable.class));
    }

    private static Specification<LeaveApplication> anyQuery() {
        return ArgumentMatchers.any();
    }

    private LeaveApplication pendingApplication(Employee who, int days) {
        return new LeaveApplication(who, type, MONDAY, WEDNESDAY, days, "x", LeaveStatus.PENDING, null);
    }


    @Test
    void aDelegateeRecommendsTheColleagueSQueueAndTheTrailSaysSo() {
        Employee boss = mock(Employee.class);
        when(boss.getId()).thenReturn(99L);
        when(boss.getFullName()).thenReturn("Ms Sadia Khan");
        ApprovalDelegation held = delegation(99L, boss);
        when(delegations.supervisorHolds(employee, 99L)).thenReturn(List.of(held));
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x",
                        LeaveStatus.PENDING_SUPERVISOR, 99L);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("EMPLOYEE");

        service.supervisorDecision(USERNAME, 7L, true, "fine");

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).recordAs(eq(USERNAME), eq(AuditAction.LEAVE_RECOMMENDED),
                eq("LEAVE_APPLICATION"), eq(7L), detail.capture());
        assertThat(detail.getValue()).contains("acting for Ms Sadia Khan").contains("DL-000021");
        verify(notifier).supervisorDecided(eq(application),
                org.mockito.ArgumentMatchers.<AppUser>any(), eq(held));
    }

    @Test
    void aWindowOverSomeoneElsesQueueDoesNotLeakIntoThisOne() {
        Employee someoneElse = mock(Employee.class);
        when(someoneElse.getId()).thenReturn(77L);
        ApprovalDelegation held = delegation(77L, someoneElse);
        when(delegations.supervisorHolds(employee, 77L)).thenReturn(List.of(held));
        LeaveApplication application =
                new LeaveApplication(employee, type, MONDAY, WEDNESDAY, 3, "x",
                        LeaveStatus.PENDING_SUPERVISOR, 99L);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.supervisorDecision(USERNAME, 7L, true, "ok"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(application.getStatus()).isEqualTo(LeaveStatus.PENDING_SUPERVISOR);
    }

    @Test
    void aDelegateeDecidesForADepartmentTheyDoNotHead() {
        Employee other = mock(Employee.class);
        Department otherDepartment = mock(Department.class);
        when(otherDepartment.getId()).thenReturn(2L);
        when(other.getId()).thenReturn(20L);
        when(other.getDepartment()).thenReturn(otherDepartment);
        when(other.getFullName()).thenReturn("Dr Shapeen Ullah");
        Employee boss = mock(Employee.class);
        when(boss.getId()).thenReturn(20L);
        when(boss.getFullName()).thenReturn("Dr Shapeen Ullah");
        ApprovalDelegation held = new ApprovalDelegation(boss, employee, DelegationScope.DECISION,
                otherDepartment, MONDAY, WEDNESDAY, null);
        setId(held, 22L);
        held.stampReference();
        when(delegations.decisionHolds(employee, 2L)).thenReturn(List.of(held));
        LeaveApplication application = pendingApplication(other, 3);
        when(applications.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(balances.findForUpdate(20L, TYPE_ID, YEAR))
                .thenReturn(Optional.of(new LeaveBalance(other, type, YEAR, 25)));
        signIn("EMPLOYEE");

        service.decide(USERNAME, 7L, true, false, null);

        assertThat(application.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).recordAs(eq(USERNAME), eq(AuditAction.LEAVE_APPROVED),
                eq("LEAVE_APPLICATION"), eq(7L), detail.capture());
        assertThat(detail.getValue()).contains("acting for Dr Shapeen Ullah").contains("DL-000022");
    }

    /** A live window over one person's recommending, as the queues see it. */
    private ApprovalDelegation delegation(long holderId, Employee holder) {
        when(holder.getFullName()).thenReturn("Ms Sadia Khan");
        ApprovalDelegation held = new ApprovalDelegation(holder, employee,
                DelegationScope.SUPERVISOR, null, MONDAY, WEDNESDAY, null);
        setId(held, 21L);
        held.stampReference();
        return held;
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

    private void setId(LeaveApplication application, long id) {
        try {
            var field = LeaveApplication.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AppUser signIn(String... roleCodes) {
        Set<AppRole> roles = Arrays.stream(roleCodes)
                .map(code -> {
                    AppRole role = mock(AppRole.class);
                    when(role.getCode()).thenReturn(code);
                    return role;
                })
                .collect(Collectors.toSet());
        AppUser user = mock(AppUser.class);
        when(user.getEmployee()).thenReturn(employee);
        when(user.getRoles()).thenReturn(roles);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        return user;
    }
}
