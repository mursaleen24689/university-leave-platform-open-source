package com.example.leaveplatform.delegation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.leave.LeaveApplication;

/**
 * A supervisor or a head of department hands their approving to a named
 * colleague for a dated window, and the platform remembers who may act for whom.
 *
 * <p>Three decisions are worth stating where the code is.
 *
 * <ul>
 *   <li><b>The delegatee acts as themself.</b> Nothing here switches the
 *       session, copies the password, or pretends one account is another. The
 *       request moves into the delegatee's own queue, they press the button, and
 *       the platform records their name beside a plain statement that they were
 *       acting for somebody else. The earlier application coped with absent officers by logging in
 *       as them, which left the audit trail naming a person who was on leave.
 *   <li><b>A window expires by comparison, not by a job.</b> Every question the
 *       leave module asks comes with today's date, so an ended window stops
 *       working at midnight of its own accord, and a future one refuses to empty
 *       a queue early. Only closing a window <i>early</i> is an action, and it
 *       belongs to the person who opened it or to the office.
 *   <li><b>The authority is the delegator's own, never more.</b> The person in
 *       the request cannot be chosen by the caller - the signed-in staff record
 *       is always the delegator - the department of a final-decision window is
 *       the one the delegator heads, and a central administrator's
 *       university-wide authority is not delegable at all: it is the fallback
 *       for the whole platform, including this screen. What the office cannot
 *       cover while a head of department is away is a missing head of
 *       department, which the organisation screen appoints.
 * </ul>
 */
@Service
public class DelegationService {

    /** A list shows the {@value} windows this person has seen most recently. */
    public static final int LIST_LIMIT = 200;

    /** How many names the colleague picker offers at once. */
    public static final int DELEGATEE_LIMIT = 50;

    /**
     * The longest window one hand-over may cover. A step that must be handed
     * over for longer than a year is not an absence, it is an organisation
     * change, and organisation changes are made on the staff record where the
     * reporting line actually lives.
     */
    public static final int MAX_WINDOW_DAYS = 366;

    private final ApprovalDelegationRepository delegations;
    private final EmployeeRepository employees;
    private final AppUserRepository users;
    private final AuditService audit;
    private final DelegationNotifier notifier;

    public DelegationService(ApprovalDelegationRepository delegations, EmployeeRepository employees,
            AppUserRepository users, AuditService audit, DelegationNotifier notifier) {
        this.delegations = delegations;
        this.employees = employees;
        this.users = users;
        this.audit = audit;
        this.notifier = notifier;
    }

    /**
     * Writes the window. The caller is always the delegator - whose approving
     * this is cannot be typed into a body - and the questions the rule asks are
     * answered in the order a person would think of them: the dates, the
     * colleague, the right to hand this particular step over, and whether
     * another window already covers the same period.
     */
    @Transactional
    public DelegationResponse create(String username, NewDelegationRequest input) {
        AppUser user = account(username);
        Employee delegator = staffRecord(user);
        LocalDate today = LocalDate.now();

        if (input.toEmployeeId() == null || input.scope() == null
                || input.startDate() == null || input.endDate() == null) {
            throw new DelegationRuleException(
                    "Choose the colleague, the step and both days of the window.");
        }
        if (Objects.equals(input.toEmployeeId(), delegator.getId())) {
            throw new DelegationRuleException(
                    "Delegating to yourself would change nothing - you already hold this approving.");
        }
        if (input.endDate().isBefore(input.startDate())) {
            throw new DelegationRuleException("The last day of the window cannot be before the first day.");
        }
        if (input.startDate().isBefore(today)) {
            throw new DelegationRuleException("A window cannot start in the past; the approving it covers"
                    + " has to be still to come. Start it today if the absence has already begun.");
        }
        if (input.endDate().isAfter(today.plusDays(MAX_WINDOW_DAYS))) {
            throw new DelegationRuleException("A hand-over cannot reach further than " + MAX_WINDOW_DAYS
                    + " days ahead. A longer absence is a change of reporting line, which the"
                    + " administration office makes on the staff record.");
        }

        Employee delegatee = employees.findByIdAndActiveTrue(input.toEmployeeId())
                .orElseThrow(() -> new DelegationRuleException(
                        "That colleague is not on the active staff, so they cannot be given a window."));
        if (users.findByEmployeeId(delegatee.getId()).filter(AppUser::isEnabled).isEmpty()) {
            throw new DelegationRuleException(delegatee.getFullName()
                    + " has no working sign-in account, so a window handed to them would reach nobody.");
        }

        // The delegator's staff row is the lock the queue hangs off: two windows
        // created for one person in the same second are checked one after the
        // other, the same way an approval locks the record it is about to write.
        employees.findByIdForUpdate(delegator.getId())
                .orElseThrow(() -> new DelegationRuleException("Your staff record is no longer active."));

        ApprovalDelegation draft = new ApprovalDelegation(delegator, delegatee, input.scope(),
                null, input.startDate(), input.endDate(), limited(input.reason()));
        if (input.scope() == DelegationScope.DECISION) {
            if (!hasRole(user, AppRole.HOD)) {
                throw new DelegationRuleException("Only a head of department has final leave decisions"
                        + " to hand over. Recommending the requests of the staff you supervise is the"
                        + " other kind of window.");
            }
            draft.coverDepartment(delegator.getDepartment());
        } else if (!employees.existsBySupervisorEmployeeIdAndActiveTrue(delegator.getId())) {
            throw new DelegationRuleException("No staff record reports to you today, so no request is"
                    + " reaching you at the supervisor step to hand over. A final-decision window is"
                    + " the other kind, and it needs the head-of-department role.");
        }
        describeConflicts(draft);

        ApprovalDelegation saved = delegations.save(draft);
        saved.stampReference();
        audit.recordAs(username, AuditAction.DELEGATION_CREATED, "APPROVAL_DELEGATION", saved.getId(),
                "Handed " + saved.getScope().label().toLowerCase(Locale.ROOT) + " over to "
                        + delegatee.getFullName() + " (" + displayNo(delegatee) + ") from "
                        + saved.getStartDate() + " to " + saved.getEndDate()
                        + (saved.getReason() == null ? "." : "; " + excerpt(saved.getReason())));
        notifier.offered(saved, user);
        return DelegationResponse.of(saved, delegator.getId());
    }

    /**
     * Closes a window early. The person who opened it can hand the approving
     * back at any time - they may be the one now sitting at their desk - and so
     * can the office. The delegatee cannot: giving back an authority was never
     * the holder's act to do.
     */
    @Transactional
    public DelegationResponse revoke(String username, Long id) {
        AppUser user = account(username);
        Employee delegator = staffRecord(user);
        ApprovalDelegation delegation = delegations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "There is no delegation number " + id + "."));
        boolean officer = hasRole(user, AppRole.CENTRAL_ADMIN);
        if (!officer && !Objects.equals(delegation.getFromEmployee().getId(), delegator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only " + delegation.getFromEmployee().getFullName()
                            + ", who opened this window, or the administration office can close it.");
        }
        if (!delegation.isActive()) {
            throw new DelegationRuleException("This window was already closed on "
                    + delegation.getRevokedAt().atZone(ZoneId.systemDefault()).toLocalDate() + ".");
        }
        delegation.revoke(delegator);
        audit.recordAs(username, AuditAction.DELEGATION_REVOKED, "APPROVAL_DELEGATION",
                delegation.getId(),
                "Closed " + delegation.getReference() + " early: " + delegation.getScope().label()
                        + " of " + delegation.getFromEmployee().getFullName() + ", held by "
                        + delegation.getToEmployee().getFullName() + ", is no longer usable.");
        notifier.revoked(delegation, user);
        return DelegationResponse.of(delegation, delegator.getId());
    }

    /** Both sides of every window this person has seen; their own screen. */
    @Transactional(readOnly = true)
    public List<DelegationResponse> mine(String username) {
        Employee person = staffRecord(account(username));
        return delegations.findMine(person.getId(), PageRequest.of(0, LIST_LIMIT)).stream()
                .map(delegation -> DelegationResponse.of(delegation, person.getId()))
                .toList();
    }

    /** Every window, for the office - read-only through the API, like the audit trail. */
    @Transactional(readOnly = true)
    public List<DelegationResponse> all(String username) {
        AppUser user = account(username);
        if (!hasRole(user, AppRole.CENTRAL_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Delegations as a whole are read by the administration office.");
        }
        Long viewer = user.getEmployee() == null ? null : user.getEmployee().getId();
        return delegations.findLatest(PageRequest.of(0, LIST_LIMIT)).stream()
                .map(delegation -> DelegationResponse.of(delegation, viewer))
                .toList();
    }

    /** The colleagues the picker offers: active staff with a working account. */
    @Transactional(readOnly = true)
    public List<DelegateeResponse> colleagues(String username, String search) {
        staffRecord(account(username));
        return employees.findSignable(like(search), PageRequest.of(0, DELEGATEE_LIMIT)).stream()
                .map(DelegateeResponse::of)
                .toList();
    }

    /**
     * The windows this person holds over one supervisor's recommending, usable
     * today. LeaveService asks the same question the queues do, so a button and
     * the list row it sits beside can never disagree about who may act.
     */
    @Transactional(readOnly = true)
    public List<ApprovalDelegation> supervisorHolds(Employee delegatee, Long supervisorEmployeeId) {
        return supervisorHolds(delegatee).stream()
                .filter(delegation -> Objects.equals(
                        delegation.getFromEmployee().getId(), supervisorEmployeeId))
                .toList();
    }

    /** The final-decision windows this person holds over one department, usable today. */
    @Transactional(readOnly = true)
    public List<ApprovalDelegation> decisionHolds(Employee delegatee, Long departmentId) {
        return decisionHolds(delegatee).stream()
                .filter(delegation -> delegation.getDepartment() != null
                        && Objects.equals(delegation.getDepartment().getId(), departmentId))
                .toList();
    }

    /** Every recommending window this person may cover today. */
    @Transactional(readOnly = true)
    public List<ApprovalDelegation> supervisorHolds(Employee delegatee) {
        return delegations.findUsableAs(delegatee.getId(), DelegationScope.SUPERVISOR, LocalDate.now());
    }

    /** Every final-decision window this person may cover today. */
    @Transactional(readOnly = true)
    public List<ApprovalDelegation> decisionHolds(Employee delegatee) {
        return delegations.findUsableAs(delegatee.getId(), DelegationScope.DECISION, LocalDate.now());
    }

    /**
     * The window this person is acting under for this request, if any - the
     * supervisor holds first, because that step names a person, then the
     * department the applicant belongs to.
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalDelegation> actingUnder(Employee delegatee, LeaveApplication application) {
        Long supervisorId = application.getSupervisorEmployeeId();
        if (supervisorId != null) {
            List<ApprovalDelegation> held = supervisorHolds(delegatee, supervisorId);
            if (!held.isEmpty()) {
                return Optional.of(held.get(0));
            }
        }
        return decisionHolds(delegatee, application.getEmployee().getDepartment().getId()).stream()
                .findFirst();
    }

    /**
     * The words the audit trail and the notice to the delegator both use: under
     * which window the person was standing in. Empty for the ordinary case
     * where nobody is acting for anybody.
     */
    public static String actingFor(ApprovalDelegation delegation) {
        return delegation == null ? "" : " (acting for " + delegation.getFromEmployee().getFullName()
                + ", " + delegation.getReference() + ")";
    }

    /**
     * One unhanded-back window per person per step, whatever its dates say: a
     * second would put the same queue in two pairs of hands at once, or hold two
     * promises for the same fortnight. A database constraint cannot ask this
     * question without locking out next year's window too - see {@code V016}.
     */
    private void describeConflicts(ApprovalDelegation draft) {
        for (ApprovalDelegation existing : delegations.findHeld(draft.getFromEmployee().getId())) {
            if (existing.getScope() != draft.getScope() || !existing.isActive()) {
                continue;
            }
            if (!existing.getStartDate().isAfter(draft.getEndDate())
                    && !existing.getEndDate().isBefore(draft.getStartDate())) {
                throw new DelegationRuleException("You already have " + existing.getScope().label()
                        + " delegated to " + existing.getToEmployee().getFullName() + " from "
                        + existing.getStartDate() + " to " + existing.getEndDate() + " ("
                        + existing.getReference() + "). Close that window first if the colleague changes.");
            }
        }
    }

    private static String displayNo(Employee person) {
        return person.getEmployeeNo() == null ? person.getFullName() : person.getEmployeeNo();
    }

    private static String excerpt(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private AppUser account(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static Employee staffRecord(AppUser user) {
        Employee person = user.getEmployee();
        if (person == null || !person.isActive()) {
            throw new DelegationRuleException(
                    "Your account has no active staff record, so the platform cannot tell whose"
                            + " approving you would be handing over.");
        }
        return person;
    }

    private static boolean hasRole(AppUser user, String roleCode) {
        return user.getRoles().stream().anyMatch(role -> roleCode.equals(role.getCode()));
    }

    private static String limited(String text) {
        String value = trimmed(text);
        if (value != null && value.length() > ApprovalDelegation.MAX_REASON) {
            throw new DelegationRuleException("Keep the reason to "
                    + ApprovalDelegation.MAX_REASON + " characters.");
        }
        return value;
    }

    /** The search box is optional; an empty one means "show me the first page". */
    private static String like(String search) {
        String value = trimmed(search);
        return value == null ? null : "%" + value.toLowerCase(Locale.ROOT) + "%";
    }

    private static String trimmed(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
