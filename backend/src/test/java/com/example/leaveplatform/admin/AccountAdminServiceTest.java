package com.example.leaveplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppRoleRepository;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.identity.PasswordHistoryRepository;
import com.example.leaveplatform.identity.PasswordPolicy;
import com.example.leaveplatform.organization.Department;

class AccountAdminServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AppRoleRepository roles = mock(AppRoleRepository.class);
    private final PasswordHistoryRepository history = mock(PasswordHistoryRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuditService audit = mock(AuditService.class);
    private final AccountAdminService service = new AccountAdminService(
            users, employees, roles, history, encoder, new PasswordPolicy(), audit);

    private final Department registrar = mock(Department.class);
    private final Employee employee = mock(Employee.class);
    private final AppRole employeeRole = role("EMPLOYEE");
    private final AppRole supervisorRole = role("SUPERVISOR");
    private final AppRole hodRole = role("HOD");
    private final AppRole clerkRole = role("PA_CLERK");
    private final AppRole departmentAdminRole = role("DEPT_ADMIN");
    private final AppRole adminRole = role("CENTRAL_ADMIN");
    private final AppRole auditorRole = role("AUDITOR");

    @BeforeEach
    void commonStubs() {
        when(registrar.getCode()).thenReturn("REGISTRAR");
        when(employee.getId()).thenReturn(7L);
        when(employee.getEmployeeNo()).thenReturn("7042");
        when(employee.getFullName()).thenReturn("Ayesha Khan");
        when(employee.getDepartment()).thenReturn(registrar);
        when(employee.isActive()).thenReturn(true);
        when(employees.findById(7L)).thenReturn(Optional.of(employee));
        when(users.findByEmployeeId(7L)).thenReturn(Optional.empty());
        when(users.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
        when(history.findByUserIdOrderByChangedAtDesc(any())).thenReturn(List.of());
        when(roles.findByCode("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(roles.findByCode("SUPERVISOR")).thenReturn(Optional.of(supervisorRole));
        when(roles.findByCode("HOD")).thenReturn(Optional.of(hodRole));
        when(roles.findByCode("PA_CLERK")).thenReturn(Optional.of(clerkRole));
        when(roles.findByCode("DEPT_ADMIN")).thenReturn(Optional.of(departmentAdminRole));
        when(roles.findByCode("CENTRAL_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roles.findByCode("AUDITOR")).thenReturn(Optional.of(auditorRole));
    }

    @Test
    void createsAnAccountWithAGeneratedTemporaryPassword() {
        IssuedCredentials issued = service.create(
                new AccountInput(7L, "A.Khan", null, List.of("HOD")), "admin");

        assertThat(issued.temporaryPassword()).hasSize(14);
        assertThat(issued.account().username()).isEqualTo("a.khan");
        assertThat(issued.account().roles()).containsExactly("EMPLOYEE", "HOD");
        assertThat(issued.account().mustChangePassword()).isTrue();
        assertThat(issued.account().enabled()).isTrue();
        verify(users).save(any(AppUser.class));
    }

    @Test
    void storesOnlyTheHashOfTheTemporaryPassword() {
        IssuedCredentials issued = service.create(
                new AccountInput(7L, "ayesha.khan", null, List.of()), "admin");

        AppUser saved = captureSaved();
        assertThat(saved.getPasswordHash()).isNotEqualTo(issued.temporaryPassword());
        assertThat(encoder.matches(issued.temporaryPassword(), saved.getPasswordHash())).isTrue();
    }

    @Test
    void acceptsAnAdministratorSuppliedTemporaryPassword() {
        IssuedCredentials issued = service.create(new AccountInput(7L, "ayesha.khan",
                "Registrar-2026-pass", List.of()), "admin");

        assertThat(issued.temporaryPassword()).isEqualTo("Registrar-2026-pass");
        assertThat(encoder.matches("Registrar-2026-pass", captureSaved().getPasswordHash())).isTrue();
    }

    @Test
    void refusesATemporaryPasswordBelowThePolicy() {
        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "ayesha.khan", "short", List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Password must be at least 12 characters.");
    }

    @Test
    void refusesATemporaryPasswordThatContainsTheUsername() {
        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "ayesha.khan", "Ayesha.khan-2026pass", List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Password must not contain the username.");
    }

    @Test
    void refusesAnEmployeeThatAlreadyHasAnAccount() {
        when(users.findByEmployeeId(7L)).thenReturn(Optional.of(mock(AppUser.class)));

        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "ayesha.khan", null, List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("That employee already has a sign-in account.");
    }

    @Test
    void refusesAnInactiveEmployee() {
        when(employee.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "ayesha.khan", null, List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("The employee is not active. Activate the record first.");
    }

    @Test
    void refusesAnUnknownEmployee() {
        when(employees.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new AccountInput(99L, "ayesha.khan", null, List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("That employee no longer exists.");
    }

    @Test
    void refusesAUsernameThatIsTaken() {
        when(users.existsByUsernameIgnoreCase("ayesha.khan")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "Ayesha.Khan", null, List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Username ayesha.khan is already taken.");
    }

    @Test
    void refusesAMalformedUsername() {
        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "a k", null, List.of()), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("Username must be 3-60 characters");
    }

    @Test
    void refusesAnUnknownRole() {
        assertThatThrownBy(() -> service.create(
                new AccountInput(7L, "ayesha.khan", null, List.of("SUPERUSER")), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Unknown role SUPERUSER.");
    }

    @Test
    void alwaysGrantsTheEmployeeRole() {
        service.create(new AccountInput(7L, "ayesha.khan", null, List.of("SUPERVISOR")), "admin");

        assertThat(captureSaved().getRoles()).extracting(AppRole::getCode)
                .containsExactlyInAnyOrder("EMPLOYEE", "SUPERVISOR");
    }

    @Test
    void resetsAPasswordAndForcesAChange() {
        AppUser existing = new AppUser(employee, "ayesha.khan",
                encoder.encode("Old-password-2026"));
        when(users.findById(3L)).thenReturn(Optional.of(existing));

        IssuedCredentials issued = service.resetPassword(3L, null, "admin");

        assertThat(issued.temporaryPassword()).hasSize(14);
        assertThat(existing.isMustChangePassword()).isTrue();
        assertThat(encoder.matches(issued.temporaryPassword(), existing.getPasswordHash())).isTrue();
        verify(history).save(any());
    }

    @Test
    void refusesToResetYourOwnPasswordFromTheAdministrationScreen() {
        AppUser self = new AppUser(employee, "admin", encoder.encode("whatever-2026-pass"));
        when(users.findById(3L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.resetPassword(3L, null, "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Use Change password for your own account.");
    }

    @Test
    void disablesAndEnablesAnotherAccount() {
        AppUser other = new AppUser(employee, "ayesha.khan",
                encoder.encode("whatever-2026-pass"));
        when(users.findById(3L)).thenReturn(Optional.of(other));

        AccountSummary disabled = service.setEnabled(3L, false, "admin");
        assertThat(disabled.enabled()).isFalse();

        AccountSummary enabled = service.setEnabled(3L, true, "admin");
        assertThat(enabled.enabled()).isTrue();
        verify(users, never()).findByUsername(any());
    }

    @Test
    void refusesToDisableYourOwnAccount() {
        AppUser self = new AppUser(employee, "admin", encoder.encode("whatever-2026-pass"));
        when(users.findById(3L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.setEnabled(3L, false, "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("You cannot disable your own account.");
        assertThat(self.isEnabled()).isTrue();
    }

    @Test
    void refusesToRemoveYourOwnAdministratorRole() {
        AppUser self = new AppUser(employee, "admin", encoder.encode("whatever-2026-pass"));
        when(users.findById(3L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.replaceRoles(3L, List.of("HOD"), "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("You cannot remove your own administrator role.");
    }

    @Test
    void replacesRolesAndKeepsTheEmployeeRole() {
        AppUser other = new AppUser(employee, "ayesha.khan",
                encoder.encode("whatever-2026-pass"));
        when(users.findById(3L)).thenReturn(Optional.of(other));

        AccountSummary summary = service.replaceRoles(3L, List.of("HOD"), "admin");

        assertThat(summary.roles()).containsExactly("EMPLOYEE", "HOD");
    }

    @Test
    void turnsTwoStepOffForSomeoneWhoHasLostTheirPhone() {
        AppUser other = new AppUser(employee, "ayesha.khan",
                encoder.encode("whatever-2026-pass"));
        other.enrolTwoStep("v1:aseedthepersoncannotreachanymore", Instant.parse("2026-11-02T04:15:00Z"));
        when(users.findById(3L)).thenReturn(Optional.of(other));

        AccountSummary cleared = service.clearTwoStep(3L, "admin");

        // The only way back into an account whose phone is gone, which is why it
        // is the one two-step action here that is not the person's own, and why
        // it is written down.
        assertThat(cleared.twoStepEnabled()).isFalse();
        assertThat(cleared.twoStepSince()).isNull();
        assertThat(other.hasTwoStep()).isFalse();
        verify(audit).recordAs(eq("admin"), eq(AuditAction.TWO_STEP_CLEARED), eq("ACCOUNT"),
                any(), eq("Turned off two-step signing for ayesha.khan; they must set it up again"
                        + " after signing in with their password alone."));
    }

    @Test
    void refusesToTurnYourOwnTwoStepOffFromTheAdministrationScreen() {
        AppUser self = new AppUser(employee, "admin", encoder.encode("whatever-2026-pass"));
        self.enrolTwoStep("v1:aseed", Instant.now());
        when(users.findById(3L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.clearTwoStep(3L, "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Turn two-step off for your own account on its own screen,"
                        + " where your app confirms it.");
        assertThat(self.hasTwoStep()).isTrue();
    }

    @Test
    void refusesToClearTwoStepOnAnAccountThatDoesNotUseIt() {
        AppUser other = new AppUser(employee, "ayesha.khan",
                encoder.encode("whatever-2026-pass"));
        when(users.findById(3L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.clearTwoStep(3L, "admin"))
                .isInstanceOf(AdminException.class)
                .hasMessage("That account does not use two-step signing.");
    }

    @Test
    void listsEmployeesWithoutAnAccount() {
        when(employees.findWithoutAccount()).thenReturn(List.of(employee));

        List<AccountCandidate> candidates = service.candidates();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).label()).isEqualTo("Ayesha Khan (7042, REGISTRAR)");
    }

    @Test
    void searchNormalisesTheQueryAndCapsHowManyAccountsItLoads() {
        when(users.search(eq("%khan%"), any(Pageable.class))).thenReturn(List.of());

        assertThat(service.search(" KHAN ")).isEmpty();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(users).search(eq("%khan%"), page.capture());
        // An empty box is the dangerous one: this query joins roles, employee and
        // department, so it gets the same ceiling as the employee search.
        assertThat(page.getValue().getPageSize()).isEqualTo(EmployeeAdminService.SEARCH_LIMIT);
        assertThat(service.search("  ")).isEmpty();
        verify(users).search(isNull(), any(Pageable.class));
    }

    private AppUser captureSaved() {
        org.mockito.ArgumentCaptor<AppUser> captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    private static AppRole role(String code) {
        AppRole role = mock(AppRole.class);
        when(role.getCode()).thenReturn(code);
        return role;
    }
}
