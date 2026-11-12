package com.example.leaveplatform.leave;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.delegation.ApprovalDelegation;
import com.example.leaveplatform.delegation.DelegationService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;

/**
 * Casual leave rules, taken from the application this platform replaces. See
 * docs/LEAVE-RULES.md for the rules and the deliberate differences.
 *
 * <p>Where a step of the workflow asks "is this your queue", the answer includes
 * the dated windows {@link DelegationService} holds: a colleague a supervisor
 * handed their recommending to sees the same rows and presses the same buttons,
 * and every decision taken that way says so in the audit trail.
 */
@Service
public class LeaveService {

    private static final Set<LeaveStatus> BLOCKING_STATUSES =
            EnumSet.of(LeaveStatus.PENDING_SUPERVISOR, LeaveStatus.PENDING, LeaveStatus.APPROVED);

    /**
     * How many rows one list may load. A department with a thousand requests in
     * the year must not send all of them to a browser, so the newest {@value
     * LIST_LIMIT} come back and the screen says the list was cut off.
     */
    public static final int LIST_LIMIT = 200;

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final LeaveApplicationRepository applications;
    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final PublicHolidayRepository holidays;
    private final AuditService audit;
    private final LeaveNotifier notifier;
    private final DelegationService delegations;

    public LeaveService(AppUserRepository users, EmployeeRepository employees,
            LeaveApplicationRepository applications,
            LeaveBalanceRepository balances, LeaveTypeRepository types,
            PublicHolidayRepository holidays, AuditService audit, LeaveNotifier notifier,
            DelegationService delegations) {
        this.users = users;
        this.employees = employees;
        this.applications = applications;
        this.balances = balances;
        this.types = types;
        this.holidays = holidays;
        this.audit = audit;
        this.notifier = notifier;
        this.delegations = delegations;
    }

    @Transactional(readOnly = true)
    public LeaveBalanceResponse balance(String username) {
        AppUser user = account(username);
        LeaveType type = casualType();
        int year = LocalDate.now().getYear();
        return balances.findByEmployeeIdAndLeaveTypeIdAndYear(user.getEmployee().getId(), type.getId(), year)
                .map(LeaveService::toBalance)
                .orElseGet(() -> new LeaveBalanceResponse(type.getCode(), type.getName(), year,
                        type.getAnnualAllowance(), 0, type.getAnnualAllowance()));
    }

    @Transactional(readOnly = true)
    public List<LeaveApplicationResponse> myApplications(String username, LeaveFilter filter) {
        AppUser user = account(username);
        return find(LeaveApplicationSpecifications.employee(user.getEmployee().getId())
                .and(LeaveApplicationSpecifications.filter(filter)),
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Transactional(readOnly = true)
    public ApprovalQueueResponse approvals(String username, LeaveFilter period) {
        AppUser user = account(username);
        Employee person = user.getEmployee();

        // A delegatee's supervisor queue is their own name plus the names they
        // are standing in for today - the same test, from the same service, as
        // the button at the end of each row applies.
        List<Long> supervisorNames = new ArrayList<>();
        supervisorNames.add(person.getId());
        delegations.supervisorHolds(person).forEach(held ->
                supervisorNames.add(held.getFromEmployee().getId()));
        List<LeaveApplicationResponse> supervisorQueue = find(
                LeaveApplicationSpecifications.supervisorIn(supervisorNames)
                        .and(LeaveApplicationSpecifications.status(LeaveStatus.PENDING_SUPERVISOR))
                        .and(LeaveApplicationSpecifications.filter(period)),
                Sort.by(Sort.Direction.ASC, "startDate").and(Sort.by(Sort.Direction.ASC, "id")));

        // The final-decision queue is the whole university's for a central
        // administrator, their own department's for an HOD, and the delegated
        // departments for whoever a window names; the period the person chose
        // narrows all of them the same way.
        Specification<LeaveApplication> waiting = LeaveApplicationSpecifications
                .status(LeaveStatus.PENDING)
                .and(LeaveApplicationSpecifications.filter(period));
        List<Long> scopeDepartments = new ArrayList<>();
        if (hasRole(user, AppRole.HOD)) {
            scopeDepartments.add(person.getDepartment().getId());
        }
        delegations.decisionHolds(person).forEach(held ->
                scopeDepartments.add(held.getDepartment().getId()));
        boolean centralAdmin = hasRole(user, AppRole.CENTRAL_ADMIN);
        List<LeaveApplicationResponse> decisionQueue = centralAdmin || !scopeDepartments.isEmpty()
                ? find(centralAdmin
                        ? waiting
                        : waiting.and(LeaveApplicationSpecifications.departmentIn(scopeDepartments)),
                Sort.by(Sort.Direction.ASC, "startDate").and(Sort.by(Sort.Direction.ASC, "id")))
                : List.of();
        return new ApprovalQueueResponse(supervisorQueue, decisionQueue);
    }

    private List<LeaveApplicationResponse> find(Specification<LeaveApplication> query, Sort order) {
        return applications.findAll(query, PageRequest.of(0, LIST_LIMIT, order)).stream()
                .map(LeaveService::toResponse)
                .toList();
    }

    @Transactional
    public LeaveApplicationResponse submit(String username, SubmitLeaveRequest request) {
        AppUser user = account(username);
        Employee employee = user.getEmployee();
        LocalDate start = request.startDate();
        LocalDate end = request.endDate() == null ? start : request.endDate();
        if (end.isBefore(start)) {
            throw new LeaveRuleException("The end date cannot be before the start date.");
        }
        if (end.isAfter(start.plusYears(1))) {
            throw new LeaveRuleException("One request cannot cover more than a year.");
        }
        if (end.getYear() != start.getYear()) {
            // A balance belongs to one year, and so does the paper this request
            // prints on. Charging a January day to December's allowance would
            // spend a quota the person did not use and refuse a request the next
            // year could have covered, so the two years are two requests.
            throw new LeaveRuleException("Leave that crosses into " + end.getYear()
                    + " needs a separate request for each year, because each year has its own balance."
                    + " Apply for " + start + " to " + start.withDayOfYear(start.lengthOfYear())
                    + ", then for " + end.withDayOfYear(1) + " to " + end + ".");
        }

        LeaveType type = casualType();
        Set<LocalDate> holidayDates = new HashSet<>(holidays.findDatesBetween(start, end));
        int days = LeaveDayCounter.countInclusive(start, end, holidayDates);
        if (days == 0) {
            throw new LeaveRuleException(
                    "These dates contain only public holidays, so there is no leave day to count.");
        }
        if (applications.existsOverlapping(employee.getId(), start, end, BLOCKING_STATUSES)) {
            throw new LeaveRuleException(
                    "You already have a pending or approved request that overlaps these dates.");
        }

        // An HOD's own request skips the supervisor step, as the earlier application did.
        Long supervisor = hasRole(user, AppRole.HOD) ? null : employee.getSupervisorEmployeeId();
        LeaveStatus status = supervisor == null ? LeaveStatus.PENDING : LeaveStatus.PENDING_SUPERVISOR;

        LeaveApplication saved = applications.save(new LeaveApplication(
                employee, type, start, end, days, clean(request.reason()), status, supervisor));
        audit.recordAs(username, AuditAction.LEAVE_SUBMITTED, "LEAVE_APPLICATION", saved.getId(),
                "Submitted " + type.getCode() + " " + days + " day(s) from " + start + " to " + end
                        + "; status " + status + ".");
        notifier.submitted(saved);
        return toResponse(saved);
    }

    @Transactional
    public void supervisorDecision(String username, Long applicationId, boolean recommend, String remarks) {
        AppUser user = account(username);
        LeaveApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> notFound(applicationId));
        if (application.getStatus() != LeaveStatus.PENDING_SUPERVISOR) {
            throw new LeaveRuleException("This request is no longer waiting for a supervisor.");
        }
        ApprovalDelegation held = null;
        if (!Objects.equals(application.getSupervisorEmployeeId(), user.getEmployee().getId())) {
            // Not the name on the request: the only other way in is a window
            // that covers today. The delegatee acts as themself, and the audit
            // detail and the notice to the delegator both say so.
            List<ApprovalDelegation> holds = delegations.supervisorHolds(
                    user.getEmployee(), application.getSupervisorEmployeeId());
            if (holds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not the supervisor this request was sent to, and no delegation"
                                + " handed it to you for today.");
            }
            held = holds.get(0);
        }
        if (recommend) {
            application.recommend(clean(remarks));
        } else {
            application.rejectBySupervisor(clean(remarks));
        }
        audit.recordAs(username,
                recommend ? AuditAction.LEAVE_RECOMMENDED : AuditAction.LEAVE_REJECTED,
                "LEAVE_APPLICATION", applicationId,
                (recommend ? "Recommended" : "Rejected") + " request of "
                        + application.getEmployee().getFullName() + " at the supervisor step."
                        + DelegationService.actingFor(held));
        notifier.supervisorDecided(application, user, held);
    }

    @Transactional
    public void decide(String username, Long applicationId, boolean approve, boolean compensate,
            String remarks) {
        AppUser user = account(username);
        LeaveApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> notFound(applicationId));
        if (application.getStatus() != LeaveStatus.PENDING) {
            throw new LeaveRuleException("This request is no longer waiting for a final decision.");
        }
        if (compensate && !approve) {
            throw new LeaveRuleException("Compensatory leave only applies when approving a request.");
        }

        boolean centralAdmin = hasRole(user, AppRole.CENTRAL_ADMIN);
        boolean departmentHod = hasRole(user, AppRole.HOD) && Objects.equals(
                user.getEmployee().getDepartment().getId(),
                application.getEmployee().getDepartment().getId());
        ApprovalDelegation held = null;
        if (!centralAdmin && !departmentHod) {
            List<ApprovalDelegation> holds = delegations.decisionHolds(
                    user.getEmployee(), application.getEmployee().getDepartment().getId());
            if (holds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the HOD of the applicant's department, a central administrator, or a"
                                + " colleague holding a final-delegation window over that department"
                                + " can decide this request.");
            }
            held = holds.get(0);
        }

        if (approve && !compensate) {
            deduct(application);
        }
        application.decide(approve, compensate, user.getEmployee().getId(), clean(remarks));
        audit.recordAs(username, approve ? AuditAction.LEAVE_APPROVED : AuditAction.LEAVE_REJECTED,
                "LEAVE_APPLICATION", applicationId,
                (approve ? (compensate ? "Approved as compensatory leave" : "Approved") : "Rejected")
                        + " the " + application.getDays() + " day(s) from "
                        + application.getStartDate() + " to " + application.getEndDate() + " requested by "
                        + application.getEmployee().getFullName()
                        + (compensate ? "; no days were deducted from the balance." : ".")
                        + DelegationService.actingFor(held));
        notifier.decided(application, user, held);
    }

    /**
     * The applicant cancels their own request while it is still waiting. The row
     * is kept and moved to WITHDRAWN rather than deleted, so the audit trail and
     * any report still show that the request existed.
     */
    @Transactional
    public LeaveApplicationResponse withdraw(String username, Long applicationId, String note) {
        AppUser user = account(username);
        LeaveApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> notFound(applicationId));
        if (!Objects.equals(application.getEmployee().getId(), user.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only withdraw your own request.");
        }
        if (!application.getStatus().isOpen()) {
            throw new LeaveRuleException(
                    "This request has already been decided, so it can no longer be withdrawn.");
        }
        // Whose queue it was leaving has to be read before the status changes.
        LeaveStatus wasWaitingFor = application.getStatus();
        application.withdraw(clean(note));
        audit.recordAs(username, AuditAction.LEAVE_WITHDRAWN, "LEAVE_APPLICATION", applicationId,
                "Withdrew the " + application.getDays() + " day(s) from " + application.getStartDate()
                        + " to " + application.getEndDate() + " before anyone decided it.");
        notifier.withdrawn(application, wasWaitingFor, user);
        return toResponse(application);
    }

    /**
     * Locks the balance row, creates it from the type's allowance on first use,
     * and refuses the approval when the remaining days are short.
     *
     * <p>The employee row is locked first. On the first approval of a year there
     * is no balance row to lock, so two decisions taken at the same moment would
     * both find nothing and both try to create it; one would then fail on the
     * unique constraint and the approver would see an error for a request that
     * was perfectly in order. The employee row is there in both cases, and it is
     * the same row the balance-correction path locks, so the two queue behind
     * each other instead of racing.
     */
    private void deduct(LeaveApplication application) {
        Employee employee = application.getEmployee();
        LeaveType type = application.getLeaveType();
        int year = application.getStartDate().getYear();
        employees.findByIdForUpdate(employee.getId());
        LeaveBalance balance = balances.findForUpdate(employee.getId(), type.getId(), year)
                .orElseGet(() -> balances.saveAndFlush(
                        new LeaveBalance(employee, type, year, type.getAnnualAllowance())));
        if (!balance.canCover(application.getDays())) {
            throw new LeaveRuleException("Cannot approve: the remaining balance is %d day(s) and the request needs %d."
                    .formatted(balance.remaining(), application.getDays()));
        }
        balance.use(application.getDays());
    }

    private AppUser account(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private LeaveType casualType() {
        return types.findByCodeAndActiveTrue(LeaveType.CASUAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "The casual leave type is not configured."));
    }

    private static boolean hasRole(AppUser user, String roleCode) {
        return user.getRoles().stream().anyMatch(role -> roleCode.equals(role.getCode()));
    }

    private static ResponseStatusException notFound(Long applicationId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "There is no leave request number " + applicationId + ".");
    }

    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LeaveBalanceResponse toBalance(LeaveBalance balance) {
        return new LeaveBalanceResponse(
                balance.getLeaveType().getCode(),
                balance.getLeaveType().getName(),
                balance.getYear(),
                balance.getAllocated(),
                balance.getUsed(),
                balance.remaining());
    }

    private static LeaveApplicationResponse toResponse(LeaveApplication application) {
        Employee employee = application.getEmployee();
        // A row that has only just been inserted has not read its trigger-written
        // number back yet, so the display copy is stamped in the same shape; the
        // database stays the only writer of the column.
        application.stampReference();
        return new LeaveApplicationResponse(
                application.getId(),
                application.getReference(),
                employee.getEmployeeNo(),
                employee.getFullName(),
                employee.getDepartment().getName(),
                application.getLeaveType().getCode(),
                application.getStartDate(),
                application.getEndDate(),
                application.getDays(),
                application.getReason(),
                application.getStatus(),
                application.getStatus().label(),
                application.isCompensated(),
                application.getRemarks(),
                application.getCreatedAt());
    }
}
