package com.example.leaveplatform.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.admin.AdminException;
import com.example.leaveplatform.admin.EmployeeAdminService;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.profile.ProfileRequest.ProposedChange;

/**
 * A member of staff asks for a correction to their own record and the office
 * decides.
 *
 * <p>One rule runs through the whole class and it is the reason the request keeps
 * a list of field names rather than relying on its columns: <b>apply only the
 * fields the request names</b>. Oracle stores an empty string as NULL, so a blank
 * column cannot say "not asked about" on its own, and an approval that writes
 * every column it was handed erases the ones nobody mentioned. It has happened exactly
 * that to staff numbers and e-mail addresses, so nothing here touches a field
 * that {@code changes()} did not list.
 *
 * <p>Three more decisions are worth stating where the code is.
 *
 * <ul>
 *   <li>A person may only propose changes to <b>their own</b> record. The
 *       applicant is taken from the session and the row is compared with it, so a
 *       typed-in employee number in a request body cannot ask about a colleague.
 *   <li>An approval <b>checks the rules again</b> at the moment it is applied. A
 *       request can sit in the queue for a fortnight, and in that time somebody
 *       else may have been given the staff number or may have left; the staff
 *       record and the office's screens also move on. Re-checking turns a
 *       would-be duplicate into a sentence the office can read instead of a
 *       constraint violation half-way through a save.
 *   <li>The person's own actions - asking and withdrawing - are not audited,
 *       because the request row is itself the permanent record of them, with its
 *       author, its time and every value it asked for. The office's decision is
 *       audited, because that is what changes a staff record.
 * </ul>
 */
@Service
public class ProfileRequestService {

    /** A list shows the newest, or the longest waiting, {@value} rows. */
    public static final int LIST_LIMIT = 200;

    private final ProfileRequestRepository requests;
    private final EmployeeRepository employees;
    private final AppUserRepository users;
    private final EmployeeAdminService rules;
    private final AuditService audit;
    private final ProfileNotifier notifier;

    public ProfileRequestService(ProfileRequestRepository requests, EmployeeRepository employees,
            AppUserRepository users, EmployeeAdminService rules, AuditService audit,
            ProfileNotifier notifier) {
        this.requests = requests;
        this.employees = employees;
        this.users = users;
        this.rules = rules;
        this.audit = audit;
        this.notifier = notifier;
    }

    /**
     * Writes the request. A field the caller left blank is not named, so the
     * approval will not touch it; a field named with a value the record already
     * holds is refused, because it would ask the office to decide nothing.
     */
    @Transactional
    public ProfileRequestResponse submit(String username, ProfileChangesRequest input) {
        AppUser user = account(username);
        Employee person = staffRecord(user);
        waitingRequest(person).ifPresent(existing -> {
            throw new ProfileRuleException("You already have a request waiting ("
                    + existing.getReference() + "). Wait for the office, or withdraw it first.");
        });

        ProfileRequest request = new ProfileRequest(person, limited(input.reason()));
        for (ProfileField field : ProfileField.values()) {
            String raw = supplied(field, input);
            if (raw == null) {
                continue;
            }
            request.propose(field, accepted(field, raw, person));
        }
        if (request.changes().isEmpty()) {
            throw new ProfileRuleException("Type the new value beside the part of your record"
                    + " that is wrong. Leave the rest blank.");
        }

        ProfileRequest saved = requests.save(request);
        saved.stampReference();
        notifier.waitingForOffice(saved, user);
        return ProfileRequestResponse.of(saved);
    }

    /** The person who asked takes it back, before the office has answered. */
    @Transactional
    public ProfileRequestResponse cancel(String username, Long id) {
        AppUser user = account(username);
        ProfileRequest request = read(id);
        ownRequest(user, request);
        stillPending(request);
        request.cancel(staffRecord(user));
        return ProfileRequestResponse.of(request);
    }

    /**
     * Applies the request to the staff record, one named field at a time, and
     * records the decision.
     *
     * <p>The request row is locked before the staff record is, and always in that
     * order, so two approvals of two different requests for one person cannot
     * each write the whole row and lose whichever went first.
     */
    @Transactional
    public ProfileRequestResponse approve(String username, Long id, String note) {
        AppUser officer = deskAccount(username);
        ProfileRequest request = read(id);
        stillPending(request);
        Employee person = employees.findByIdForUpdate(request.getEmployee().getId())
                .orElseThrow(() -> new ProfileRuleException(
                        "The staff record this request is about no longer exists."));
        String askedFor = ProfileField.describe(request.changedFields());
        String who = person.getFullName();

        for (ProposedChange change : request.changes()) {
            change.field().writeTo(person, stillAllowed(change.field(), change.value(), person));
        }
        request.approve(staffRecord(officer), limited(note));
        audit.recordAs(username, AuditAction.PROFILE_REQUEST_APPROVED, "PROFILE_REQUEST", request.getId(),
                "Approved " + request.getReference() + " for " + who + ": changed " + askedFor + ".");
        notifier.approved(request, officer);
        return ProfileRequestResponse.of(request);
    }

    /** Refuses, with a reason - a refusal nobody can act on brings the same request back. */
    @Transactional
    public ProfileRequestResponse reject(String username, Long id, String note) {
        AppUser officer = deskAccount(username);
        ProfileRequest request = read(id);
        stillPending(request);
        String why = requiredText(note, "Say why, so the person knows what to put right.");
        request.reject(staffRecord(officer), why);
        audit.recordAs(username, AuditAction.PROFILE_REQUEST_REJECTED, "PROFILE_REQUEST", request.getId(),
                "Refused " + request.getReference() + " for " + request.getEmployee().getFullName()
                        + " (" + ProfileField.describe(request.changedFields()) + "): " + excerpt(why));
        notifier.refused(request, officer, why);
        return ProfileRequestResponse.of(request);
    }

    @Transactional(readOnly = true)
    public List<ProfileRequestResponse> mine(String username) {
        AppUser user = account(username);
        Employee person = user.getEmployee();
        if (person == null) {
            return List.of();
        }
        return requests.findLatestByEmployee(person.getId(), PageRequest.of(0, LIST_LIMIT)).stream()
                .map(ProfileRequestResponse::of)
                .toList();
    }

    /** What the university holds about this person, so they can see it before asking. */
    @Transactional(readOnly = true)
    public ProfileRecordResponse record(String username) {
        return ProfileRecordResponse.of(staffRecord(account(username)));
    }

    /** The office's queue, longest waiting first. */
    @Transactional(readOnly = true)
    public List<ProfileRequestResponse> queue() {
        return requests.findQueue(ProfileRequestStatus.PENDING, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(ProfileRequestResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileRequestResponse> all() {
        return requests.findLatest(PageRequest.of(0, LIST_LIMIT)).stream()
                .map(ProfileRequestResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public long waitingForOffice() {
        return requests.countByStatus(ProfileRequestStatus.PENDING);
    }

    /**
     * The request opened up, with what the record says about each named field at
     * this moment. Readable by the person who asked and by the office, and by
     * nobody else.
     */
    @Transactional(readOnly = true)
    public ProfileRequestDetail detail(String username, Long id) {
        AppUser user = account(username);
        ProfileRequest request = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "There is no record-change request number " + id + "."));
        boolean own = user.getEmployee() != null
                && Objects.equals(request.getEmployee().getId(), user.getEmployee().getId());
        if (!own && !hasRole(user, AppRole.CENTRAL_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the person who asked and the administration office can read this request.");
        }
        List<ProfileFieldChange> changes = new ArrayList<>();
        for (ProposedChange change : request.changes()) {
            changes.add(ProfileFieldChange.of(change.field(),
                    change.field().readFrom(request.getEmployee()), change.value()));
        }
        return new ProfileRequestDetail(ProfileRequestResponse.of(request), List.copyOf(changes));
    }

    /** The value the caller offered for this field, or null when they left it out. */
    private static String supplied(ProfileField field, ProfileChangesRequest input) {
        String raw = switch (field) {
            case FULL_NAME -> input.fullName();
            case EMPLOYEE_NO -> input.employeeNo();
            case DESIGNATION -> input.designation();
            case EMAIL -> input.email();
            case PHONE -> input.phone();
        };
        return trimmed(raw);
    }

    /**
     * The rules a value has to satisfy, plus the one that only makes sense at the
     * moment somebody asks: proposing the value the record already holds asks the
     * office to decide nothing.
     */
    private String accepted(ProfileField field, String raw, Employee person) {
        String value = stillAllowed(field, raw, person);
        String current = field.readFrom(person);
        if (value.equals(current)) {
            throw new ProfileRuleException("Your " + field.label().toLowerCase(Locale.ROOT)
                    + " is already " + current + ". Type the value you would like instead,"
                    + " or leave it blank.");
        }
        return value;
    }

    /**
     * The rules a value has to satisfy, checked again when the office approves.
     * A staff number or an e-mail address can have gone to somebody else while
     * the request waited, and a record can have been corrected in the meantime;
     * either way the office has to be told rather than the transaction failing.
     *
     * <p>The rules are the ones the administration screens use, taken from
     * {@link EmployeeAdminService} rather than written again here, so a value
     * cannot be legal in one place and refused in the other. They report an
     * unusable value as an {@link AdminException}, which both screens already
     * turn into a sentence.
     */
    private String stillAllowed(ProfileField field, String raw, Employee person) {
        String value = switch (field) {
            case EMPLOYEE_NO -> rules.checkEmployeeNo(raw, person.getId());
            case EMAIL -> rules.checkEmail(raw, person.getId());
            case PHONE -> rules.checkPhone(raw);
            case FULL_NAME, DESIGNATION -> EmployeeAdminService.checkRequired(raw, field.label());
        };
        if (value.length() > field.maxLength()) {
            throw new ProfileRuleException(field.label() + " cannot be longer than "
                    + field.maxLength() + " characters.");
        }
        return value;
    }

    private Optional<ProfileRequest> waitingRequest(Employee person) {
        return requests.findFirstByEmployeeIdAndStatus(person.getId(), ProfileRequestStatus.PENDING);
    }

    private ProfileRequest read(Long id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "There is no record-change request number " + id + "."));
    }

    private AppUser account(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AppUser deskAccount(String username) {
        AppUser user = account(username);
        if (!hasRole(user, AppRole.CENTRAL_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Corrections to a staff record are decided by the administration office.");
        }
        return user;
    }

    private void ownRequest(AppUser user, ProfileRequest request) {
        Employee person = user.getEmployee();
        if (person == null || !Objects.equals(request.getEmployee().getId(), person.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This request was not made by you.");
        }
    }

    private static void stillPending(ProfileRequest request) {
        if (!request.getStatus().isPending()) {
            throw new ProfileRuleException("This request was already answered ("
                    + request.getStatus().label().toLowerCase(Locale.ROOT) + ").");
        }
    }

    private static Employee staffRecord(AppUser user) {
        Employee person = user.getEmployee();
        if (person == null || !person.isActive()) {
            throw new ProfileRuleException(
                    "Your account has no active staff record, so there is nothing here for you to correct.");
        }
        return person;
    }

    private static String requiredText(String text, String missing) {
        String value = trimmed(text);
        if (value == null) {
            throw new ProfileRuleException(missing);
        }
        return value;
    }

    private static String limited(String text) {
        String value = trimmed(text);
        if (value == null) {
            return null;
        }
        if (value.length() > ProfileRequest.MAX_REASON) {
            throw new ProfileRuleException("Keep this to " + ProfileRequest.MAX_REASON + " characters.");
        }
        return value;
    }

    private static String excerpt(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private static boolean hasRole(AppUser user, String roleCode) {
        return user.getRoles().stream().anyMatch(role -> roleCode.equals(role.getCode()));
    }

    private static String trimmed(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
