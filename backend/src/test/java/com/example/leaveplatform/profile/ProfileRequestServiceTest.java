package com.example.leaveplatform.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import com.example.leaveplatform.organization.Department;

/**
 * What a person may ask to have put right, and what an approval is allowed to
 * touch.
 *
 * <p>The case the whole class is written around is a real mistake made in that earlier application: a
 * request that names one field must leave every other column of the staff record
 * alone. A mocked repository accepts any column a test hands it, so the promise
 * is checked on the {@link Employee} mock instead - the fields that were not
 * asked about must never be written at all.
 *
 * <p>The other cases are the ones a friendly screen would never show: a colleague
 * who types somebody else's request number, a staff account that asks to be
 * treated as the office, a request that has already been answered, and a staff
 * number that went to a different person while the request waited.
 */
class ProfileRequestServiceTest {

    private static final String STAFF = "sara.ahmed";
    private static final String DESK = "admin";
    private static final String OTHER = "someone.else";
    private static final long STAFF_ID = 11L;
    private static final long DESK_ID = 22L;
    private static final long OTHER_ID = 33L;

    private final ProfileRequestRepository requests = mock(ProfileRequestRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EmployeeAdminService rules = mock(EmployeeAdminService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ProfileNotifier notifier = mock(ProfileNotifier.class);

    private final Department department = mock(Department.class);
    private final Employee staff = mock(Employee.class);
    private final Employee deskMember = mock(Employee.class);
    private final Employee otherPerson = mock(Employee.class);

    private final ProfileRequestService service =
            new ProfileRequestService(requests, employees, users, rules, audit, notifier);

    @BeforeEach
    void commonStubs() {
        when(department.getId()).thenReturn(1L);
        when(department.getName()).thenReturn("Business Administration");
        holds(staff, STAFF_ID, "Sara Ahmed", "ADMIN-001", "sara.ahmed@example.edu", "0300-0000000");
        holds(deskMember, DESK_ID, "Nadia Ahmed", "ADMIN-002", "nadia@example.edu", null);
        holds(otherPerson, OTHER_ID, "Another Person", "CS-003", "other@example.edu", null);
        when(staff.getDesignation()).thenReturn("Office Superintendent");

        AppUser staffAccount = account(STAFF, staff, false);
        AppUser deskAccount = account(DESK, deskMember, true);
        AppUser otherAccount = account(OTHER, otherPerson, false);
        when(users.findByUsername(STAFF)).thenReturn(Optional.of(staffAccount));
        when(users.findByUsername(DESK)).thenReturn(Optional.of(deskAccount));
        when(users.findByUsername(OTHER)).thenReturn(Optional.of(otherAccount));
        when(users.findEnabledWithRole(AppRole.CENTRAL_ADMIN)).thenReturn(List.of(deskAccount));
        when(employees.findByIdForUpdate(STAFF_ID)).thenReturn(Optional.of(staff));

        // The shared rules, answered the way the real ones answer, so the tests
        // show which rule was consulted without restating how it works.
        when(rules.checkEmployeeNo(any(), any())).thenAnswer(call -> {
            String raw = call.getArgument(0);
            if ("MET-9999".equals(raw == null ? null : raw.trim())) {
                throw new AdminException("Staff number MET-9999 already belongs to Ali Ahmed.");
            }
            return raw == null || raw.isBlank() ? null : raw.trim();
        });
        when(rules.checkEmail(any(), any())).thenAnswer(call -> {
            String raw = call.getArgument(0);
            if (raw != null && raw.contains("taken@")) {
                throw new AdminException("E-mail taken@app.edu already belongs to Ali Ahmed.");
            }
            return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase(java.util.Locale.ROOT);
        });
        when(rules.checkPhone(any())).thenAnswer(call -> {
            String raw = call.getArgument(0);
            return raw == null || raw.isBlank() ? null : raw.trim();
        });
    }

    /** The record as the screens see it: an identity, a department and five values. */
    private void holds(Employee person, long id, String name, String number, String email, String phone) {
        when(person.getId()).thenReturn(id);
        when(person.getFullName()).thenReturn(name);
        when(person.getEmployeeNo()).thenReturn(number);
        when(person.getEmail()).thenReturn(email);
        when(person.getPhone()).thenReturn(phone);
        when(person.getDesignation()).thenReturn("Lecturer");
        when(person.getDepartment()).thenReturn(department);
        when(person.isActive()).thenReturn(true);
    }

    private AppUser account(String username, Employee person, boolean desk) {
        AppUser user = mock(AppUser.class);
        AppRole role = mock(AppRole.class);
        when(role.getCode()).thenReturn(desk ? AppRole.CENTRAL_ADMIN : "EMPLOYEE");
        when(user.getUsername()).thenReturn(username);
        when(user.getEmployee()).thenReturn(person);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(user.isEnabled()).thenReturn(true);
        return user;
    }

    /** A row as the database would hand it back, with the fields it names. */
    private ProfileRequest stored(ProfileRequestStatus outcome, Employee person,
            ProfileField... named) {
        ProfileRequest request = new ProfileRequest(person, "My number is wrong.");
        for (ProfileField field : named) {
            request.propose(field, switch (field) {
                case FULL_NAME -> "Sara Ahmed Khan";
                case EMPLOYEE_NO -> "MET-1234";
                case DESIGNATION -> "Senior Office Superintendent";
                case EMAIL -> "sara.ahmed@app.edu";
                case PHONE -> "0300-2222222";
            });
        }
        setId(request, 7L);
        request.stampReference();
        switch (outcome) {
            case PENDING -> {
            }
            case APPROVED -> request.approve(deskMember, "Corrected as asked.");
            case REJECTED -> request.reject(deskMember, "The number is right as it stands.");
            case CANCELLED -> request.cancel(person);
        }
        return request;
    }

    private void setId(ProfileRequest request, long id) {
        try {
            var field = ProfileRequest.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(request, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ProfileChangesRequest ask(String name, String number, String role, String email,
            String phone) {
        return new ProfileChangesRequest(name, number, role, email, phone, "It was entered wrong.");
    }

    @Test
    void aNewRequestIsNumberedFromItsOwnIdAndGoesToTheOffice() {
        when(requests.save(any())).thenAnswer(call -> {
            ProfileRequest saved = call.getArgument(0);
            setId(saved, 42L);
            return saved;
        });

        ProfileRequestResponse result = service.submit(STAFF, ask(null, null, null, null, "0300-2222222"));

        assertThat(result.reference()).isEqualTo("PR-000042");
        assertThat(result.status()).isEqualTo(ProfileRequestStatus.PENDING);
        assertThat(result.changedFields()).isEqualTo("PHONE");
        assertThat(result.changedLabels()).isEqualTo("phone");
        assertThat(result.employeeName()).isEqualTo("Sara Ahmed");
        verify(notifier).waitingForOffice(any(), any());
    }

    @Test
    void onlyTheFieldsThatWereTypedAreNamedAndTheRestAreNotPartOfTheRequest() {
        when(requests.save(any())).thenAnswer(call -> {
            ProfileRequest saved = call.getArgument(0);
            setId(saved, 43L);
            return saved;
        });

        ProfileRequestResponse result = service.submit(STAFF,
                ask("Sara Ahmed Khan", null, null, "  SARA.K@EXAMPLE.EDU  ", null));

        assertThat(result.changedFields()).isEqualTo("FULL_NAME,EMAIL");
        assertThat(result.changedLabels()).isEqualTo("name and e-mail");
        assertThat(result.fieldCount()).isEqualTo(2);

        ArgumentCaptor<ProfileRequest> written = ArgumentCaptor.forClass(ProfileRequest.class);
        verify(requests).save(written.capture());
        assertThat(written.getValue().changes())
                .extracting(ProfileRequest.ProposedChange::value)
                .containsExactly("Sara Ahmed Khan", "sara.k@example.edu");
        // The columns nobody was asked about are not even offered to the office.
        assertThat(written.getValue().changes())
                .extracting(change -> change.field().name())
                .doesNotContain("EMPLOYEE_NO", "DESIGNATION", "PHONE");
    }

    @Test
    void aRequestThatNamesNothingIsRefusedRatherThanSentAsAnEmptyEnvelope() {
        assertThatThrownBy(() -> service.submit(STAFF, ask(null, "  ", null, null, "")))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("Type the new value beside the part of your record that is wrong."
                        + " Leave the rest blank.");
        verify(requests, never()).save(any());
    }

    @Test
    void aSecondRequestWaitsUntilTheFirstIsAnswered() {
        when(requests.findFirstByEmployeeIdAndStatus(STAFF_ID, ProfileRequestStatus.PENDING))
                .thenReturn(Optional.of(stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE)));

        assertThatThrownBy(() -> service.submit(STAFF, ask(null, null, null, null, "0300-9999999")))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("You already have a request waiting (PR-000007)."
                        + " Wait for the office, or withdraw it first.");
        verify(requests, never()).save(any());
    }

    @Test
    void aValueTheRecordAlreadyHoldsAsksTheOfficeToDecideNothing() {
        assertThatThrownBy(() -> service.submit(STAFF, ask(null, null, null, null, " 0300-0000000 ")))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("Your phone is already 0300-0000000. Type the value you would like"
                        + " instead, or leave it blank.");
    }

    @Test
    void theSameRulesTheOfficeUsesApplyToWhatAPersonProposes() {
        assertThatThrownBy(() -> service.submit(STAFF, ask(null, "MET-9999", null, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessage("Staff number MET-9999 already belongs to Ali Ahmed.");
        assertThatThrownBy(() -> service.submit(STAFF, ask(null, null, null, "taken@app.edu", null)))
                .isInstanceOf(AdminException.class)
                .hasMessage("E-mail taken@app.edu already belongs to Ali Ahmed.");
        verify(rules).checkEmployeeNo("MET-9999", STAFF_ID);
        verify(rules).checkEmail("taken@app.edu", STAFF_ID);
        verify(requests, never()).save(any());
    }

    @Test
    void aValueWiderThanTheColumnIsRefusedBeforeTheDatabaseIsTried() {
        assertThatThrownBy(() -> service.submit(STAFF, ask("x".repeat(151), null, null, null, null)))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("Name cannot be longer than 150 characters.");
        assertThatThrownBy(() -> service.submit(STAFF, ask(null, null, "x".repeat(101), null, null)))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("Designation cannot be longer than 100 characters.");
    }

    @Test
    void anAccountWithNoActiveStaffRecordHasNothingToCorrect() {
        AppUser lonely = account("nobody", null, false);
        when(users.findByUsername("nobody")).thenReturn(Optional.of(lonely));

        assertThatThrownBy(() -> service.submit("nobody", ask(null, null, null, null, "0300-1111111")))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("Your account has no active staff record, so there is nothing here"
                        + " for you to correct.");
    }

    @Test
    void approvingChangesOnlyTheFieldThatWasAskedAbout() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        ProfileRequestResponse result = service.approve(DESK, 7L, "Done.");

        verify(staff).changePhone("0300-2222222");
        verify(staff, never()).changeFullName(any());
        verify(staff, never()).changeEmployeeNo(any());
        verify(staff, never()).changeDesignation(any());
        verify(staff, never()).changeEmail(any());
        assertThat(result.status()).isEqualTo(ProfileRequestStatus.APPROVED);
        assertThat(result.statusLabel()).isEqualTo("Approved");
        assertThat(result.decidedByName()).isEqualTo("Nadia Ahmed");
        verify(audit).recordAs(eq(DESK), eq(AuditAction.PROFILE_REQUEST_APPROVED),
                eq("PROFILE_REQUEST"), eq(7L), any());
        verify(notifier).approved(eq(request), any());
    }

    @Test
    void anApprovalLocksTheRequestAndThenTheStaffRecord() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff,
                ProfileField.FULL_NAME, ProfileField.EMAIL);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        service.approve(DESK, 7L, null);

        verify(requests).findByIdForUpdate(7L);
        verify(employees).findByIdForUpdate(STAFF_ID);
        verify(staff).changeFullName("Sara Ahmed Khan");
        verify(staff).changeEmail("sara.ahmed@app.edu");
    }

    @Test
    void aNumberThatWentToSomeoneElseWhileTheRequestWaitedIsRefusedAtTheMomentOfApproving() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.EMPLOYEE_NO);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(rules.checkEmployeeNo("MET-1234", STAFF_ID))
                .thenThrow(new AdminException("Staff number MET-1234 already belongs to Ali Ahmed."));

        assertThatThrownBy(() -> service.approve(DESK, 7L, null))
                .isInstanceOf(AdminException.class)
                .hasMessage("Staff number MET-1234 already belongs to Ali Ahmed.");

        verify(staff, never()).changeEmployeeNo(any());
        assertThat(request.getStatus()).isEqualTo(ProfileRequestStatus.PENDING);
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());
    }

    @Test
    void aStaffAccountCannotApproveOrRefuseEvenWhenItKnowsTheNumber() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(STAFF, 7L, "I approve myself."))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
                .hasMessageContaining("decided by the administration office");
        assertThatThrownBy(() -> service.reject(STAFF, 7L, "No."))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());
        verify(staff, never()).changePhone(any());
    }

    @Test
    void anAlreadyAnsweredRequestIsNotAppliedTwice() {
        ProfileRequest request = stored(ProfileRequestStatus.APPROVED, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(DESK, 7L, null))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("This request was already answered (approved).");
        assertThatThrownBy(() -> service.reject(DESK, 7L, "Changed my mind."))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("This request was already answered (approved).");
        verify(staff, never()).changePhone(any());
    }

    @Test
    void aRefusalSaysWhyOrItIsNotARefusal() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(DESK, 7L, "   "))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("Say why, so the person knows what to put right.");

        ProfileRequestResponse result = service.reject(DESK, 7L, "The number is right as it stands.");
        assertThat(result.status()).isEqualTo(ProfileRequestStatus.REJECTED);
        assertThat(result.decisionNote()).isEqualTo("The number is right as it stands.");
        verify(audit).recordAs(eq(DESK), eq(AuditAction.PROFILE_REQUEST_REJECTED),
                eq("PROFILE_REQUEST"), eq(7L), any());
        verify(notifier).refused(eq(request), any(), eq("The number is right as it stands."));
        verify(staff, never()).changePhone(any());
    }

    @Test
    void thePersonWhoAskedCanWithdrawItAndNobodyElseCan() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(request));

        ProfileRequestResponse result = service.cancel(STAFF, 7L);

        assertThat(result.status()).isEqualTo(ProfileRequestStatus.CANCELLED);
        assertThat(result.statusLabel()).isEqualTo("Withdrawn");
        assertThat(request.getDecidedBy().getId()).isEqualTo(STAFF_ID);
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());

        ProfileRequest another = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(8L)).thenReturn(Optional.of(another));
        assertThatThrownBy(() -> service.cancel(OTHER, 8L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void aWithdrawnRequestCannotBeWithdrawnAgainAndAnApprovedOneCannotBeTakenBack() {
        ProfileRequest cancelled = stored(ProfileRequestStatus.CANCELLED, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.cancel(STAFF, 7L))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("This request was already answered (withdrawn).");

        ProfileRequest approved = stored(ProfileRequestStatus.APPROVED, staff, ProfileField.PHONE);
        when(requests.findByIdForUpdate(9L)).thenReturn(Optional.of(approved));
        assertThatThrownBy(() -> service.cancel(STAFF, 9L))
                .isInstanceOf(ProfileRuleException.class)
                .hasMessage("This request was already answered (approved).");
    }

    @Test
    void aColleagueWhoTypesTheNumberIsRefusedRatherThanShownTheProposal() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findById(7L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.detail(OTHER, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.detail(STAFF, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        assertThat(service.detail(STAFF, 7L).request().reference()).isEqualTo("PR-000007");
        assertThat(service.detail(DESK, 7L).request().reference()).isEqualTo("PR-000007");
    }

    /**
     * The comparison the office decides on carries what the record says at this
     * moment, not what it said when the request was written - a value corrected
     * by hand in the meantime has to be visible, or the approval is a guess.
     */
    @Test
    void theDetailPutsTheCurrentValueBesideTheProposedOne() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff,
                ProfileField.PHONE, ProfileField.EMPLOYEE_NO);
        when(requests.findById(7L)).thenReturn(Optional.of(request));

        ProfileRequestDetail detail = service.detail(DESK, 7L);

        assertThat(detail.changes()).hasSize(2);
        // The fields come back in the order the platform declares them, not the
        // order they were typed, so the office reads the same list every time.
        assertThat(detail.changes().get(0).label()).isEqualTo("Staff number");
        assertThat(detail.changes().get(0).current()).isEqualTo("ADMIN-001");
        assertThat(detail.changes().get(0).proposed()).isEqualTo("MET-1234");
        assertThat(detail.changes().get(1).field()).isEqualTo("PHONE");
        assertThat(detail.changes().get(1).current()).isEqualTo("0300-0000000");
        assertThat(detail.changes().get(1).proposed()).isEqualTo("0300-2222222");
        assertThat(detail.request().changedLabels()).isEqualTo("staff number and phone");
        assertThat(detail.request().reason()).isEqualTo("My number is wrong.");
    }

    @Test
    void theRecordShownToAPersonIsTheirOwnLiveRecord() {
        ProfileRecordResponse record = service.record(STAFF);

        assertThat(record.fullName()).isEqualTo("Sara Ahmed");
        assertThat(record.employeeNo()).isEqualTo("ADMIN-001");
        assertThat(record.departmentName()).isEqualTo("Business Administration");
        assertThat(record.phone()).isEqualTo("0300-0000000");
    }

    @Test
    void aStaffListIsAlwaysTakenFromTheSessionAndNeverFromTheCaller() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findLatestByEmployee(eq(STAFF_ID), any(Pageable.class))).thenReturn(List.of(request));

        assertThat(service.mine(STAFF)).singleElement()
                .satisfies(found -> assertThat(found.reference()).isEqualTo("PR-000007"));
        verify(requests).findLatestByEmployee(eq(STAFF_ID), any(Pageable.class));
        verify(requests, never()).findLatestByEmployee(eq(OTHER_ID), any(Pageable.class));
    }

    @Test
    void anAccountWithNoStaffRecordHasAnEmptyListRatherThanAnError() {
        AppUser lonely = account("nobody", null, false);
        when(users.findByUsername("nobody")).thenReturn(Optional.of(lonely));

        assertThat(service.mine("nobody")).isEmpty();
        verify(requests, never()).findLatestByEmployee(anyLong(), any(Pageable.class));
    }

    @Test
    void theQueueAsksTheDatabaseForThePendingRequestsAndCountsThem() {
        ProfileRequest request = stored(ProfileRequestStatus.PENDING, staff, ProfileField.PHONE);
        when(requests.findQueue(eq(ProfileRequestStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(request));
        when(requests.countByStatus(ProfileRequestStatus.PENDING)).thenReturn(4L);

        assertThat(service.queue()).singleElement()
                .satisfies(found -> assertThat(found.employeeName()).isEqualTo("Sara Ahmed"));
        assertThat(service.waitingForOffice()).isEqualTo(4L);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(requests).findQueue(eq(ProfileRequestStatus.PENDING), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(ProfileRequestService.LIST_LIMIT);
    }
}
