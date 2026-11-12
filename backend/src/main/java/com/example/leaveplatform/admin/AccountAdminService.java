package com.example.leaveplatform.admin;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppRoleRepository;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.identity.PasswordHistory;
import com.example.leaveplatform.identity.PasswordHistoryRepository;
import com.example.leaveplatform.identity.PasswordPolicy;
import com.example.leaveplatform.identity.WeakPasswordException;

/**
 * Opens and maintains sign-in accounts for employee records created through the
 * administration screen. A new account always starts with a temporary password
 * and must change it at the first sign-in; the password is stored only as a
 * BCrypt hash and is shown to the administrator once.
 */
@Service
public class AccountAdminService {

    private static final Pattern USERNAME =
            Pattern.compile("[a-z0-9][a-z0-9._-]{2,59}");

    /** Roles an administrator may grant, in the order the screen lists them. */
    public static final List<String> ASSIGNABLE_ROLES = List.of(
            "EMPLOYEE", "SUPERVISOR", "HOD", "PA_CLERK", "DEPT_ADMIN", "CENTRAL_ADMIN", "AUDITOR");

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMPORARY_PASSWORD_LENGTH = 14;
    private static final int HISTORY_DEPTH = 5;

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final AppRoleRepository roles;
    private final PasswordHistoryRepository history;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public AccountAdminService(AppUserRepository users, EmployeeRepository employees,
            AppRoleRepository roles, PasswordHistoryRepository history,
            PasswordEncoder passwordEncoder, PasswordPolicy policy, AuditService audit) {
        this.users = users;
        this.employees = employees;
        this.roles = roles;
        this.history = history;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AccountSummary> search(String query) {
        String pattern = StringUtils.hasText(query)
                ? "%" + query.trim().toLowerCase(Locale.ROOT) + "%"
                : null;
        // The same ceiling as the employee search beside it: this query joins
        // roles, employee and department, so an empty box must not load them all.
        return users.search(pattern, PageRequest.of(0, EmployeeAdminService.SEARCH_LIMIT)).stream()
                .map(AccountAdminService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountCandidate> candidates() {
        return employees.findWithoutAccount().stream()
                .map(employee -> new AccountCandidate(employee.getId(), employee.getEmployeeNo(),
                        employee.getFullName(), employee.getDepartment().getCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> assignableRoles() {
        return ASSIGNABLE_ROLES;
    }

    @Transactional
    public IssuedCredentials create(AccountInput input, String actorUsername) {
        if (input.employeeId() == null) {
            throw new AdminException("Choose the employee this account belongs to.");
        }
        Employee employee = employees.findById(input.employeeId())
                .orElseThrow(() -> new AdminException("That employee no longer exists."));
        if (!employee.isActive()) {
            throw new AdminException("The employee is not active. Activate the record first.");
        }
        if (users.findByEmployeeId(employee.getId()).isPresent()) {
            throw new AdminException("That employee already has a sign-in account.");
        }
        String username = username(input.username());
        String temporaryPassword = input.temporaryPassword();
        if (!StringUtils.hasText(temporaryPassword)) {
            temporaryPassword = generatePassword(username);
        } else {
            temporaryPassword = checkedPassword(temporaryPassword.trim(), username);
        }
        AppUser user = new AppUser(employee, username, passwordEncoder.encode(temporaryPassword));
        user.replaceRoles(resolveRoles(input.roles()));
        users.save(user);
        AccountSummary summary = toSummary(user);
        audit.recordAs(actorUsername, AuditAction.ACCOUNT_CREATED, "ACCOUNT", user.getId(),
                "Created account " + username + " for " + employee.getFullName() + " with roles "
                        + String.join(", ", summary.roles()) + ".");
        return new IssuedCredentials(summary, temporaryPassword);
    }

    @Transactional
    public IssuedCredentials resetPassword(Long accountId, String temporaryPassword, String actorUsername) {
        AppUser user = account(accountId);
        refuseSelfChange(user, actorUsername, "Use Change password for your own account.");
        String password = StringUtils.hasText(temporaryPassword)
                ? checkedPassword(temporaryPassword, user.getUsername())
                : generatePassword(user.getUsername());
        recordHistory(user);
        user.resetPassword(passwordEncoder.encode(password));
        audit.recordAs(actorUsername, AuditAction.PASSWORD_RESET, "ACCOUNT", user.getId(),
                "Issued a new temporary password for " + user.getUsername() + ".");
        return new IssuedCredentials(toSummary(user), password);
    }

    @Transactional
    public AccountSummary setEnabled(Long accountId, boolean enabled, String actorUsername) {
        AppUser user = account(accountId);
        if (!enabled) {
            refuseSelfChange(user, actorUsername, "You cannot disable your own account.");
        }
        if (enabled) {
            user.enable();
        } else {
            user.disable();
        }
        audit.recordAs(actorUsername,
                enabled ? AuditAction.ACCOUNT_ACTIVATED : AuditAction.ACCOUNT_DEACTIVATED,
                "ACCOUNT", user.getId(),
                (enabled ? "Enabled " : "Disabled ") + "account " + user.getUsername() + ".");
        return toSummary(user);
    }

    @Transactional
    public AccountSummary replaceRoles(Long accountId, List<String> roleCodes, String actorUsername) {
        AppUser user = account(accountId);
        Collection<AppRole> resolved = resolveRoles(roleCodes);
        boolean keepsAdmin = resolved.stream()
                .anyMatch(role -> AppRole.CENTRAL_ADMIN.equals(role.getCode()));
        if (user.getUsername().equalsIgnoreCase(actorUsername) && !keepsAdmin) {
            throw new AdminException("You cannot remove your own administrator role.");
        }
        user.replaceRoles(resolved);
        AccountSummary summary = toSummary(user);
        audit.recordAs(actorUsername, AuditAction.ROLES_REPLACED, "ACCOUNT", user.getId(),
                "Set roles of " + user.getUsername() + " to " + String.join(", ", summary.roles()) + ".");
        return summary;
    }

    /**
     * Turns two-step signing off for somebody else, which is the way back for a
     * person whose phone is gone, whose seed this platform can no longer read,
     * or who left the university with the only authenticator app that had it.
     *
     * <p>It cannot be done to one's own account: an administrator's signed-in
     * browser is exactly the thing a second factor is meant to outlast, and the
     * own screen asks for the app's number as well as the password. It cannot
     * be done the other way either - only the person holding a phone can enrol
     * it, so there is no endpoint that turns this on for somebody else and no
     * way for an office to enrol a device a colleague has never seen.
     */
    @Transactional
    public AccountSummary clearTwoStep(Long accountId, String actorUsername) {
        AppUser user = account(accountId);
        refuseSelfChange(user, actorUsername,
                "Turn two-step off for your own account on its own screen, where your app confirms it.");
        if (!user.hasTwoStep()) {
            throw new AdminException("That account does not use two-step signing.");
        }
        user.clearTwoStep();
        audit.recordAs(actorUsername, AuditAction.TWO_STEP_CLEARED, "ACCOUNT", user.getId(),
                "Turned off two-step signing for " + user.getUsername()
                        + "; they must set it up again after signing in with their password alone.");
        return toSummary(user);
    }

    private AppUser account(Long accountId) {
        return users.findById(accountId)
                .orElseThrow(() -> new AdminException("There is no such account."));
    }

    private static void refuseSelfChange(AppUser user, String actorUsername, String message) {
        if (user.getUsername().equalsIgnoreCase(actorUsername)) {
            throw new AdminException(message);
        }
    }

    private String username(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new AdminException("Username is required.");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(value).matches()) {
            throw new AdminException(
                    "Username must be 3-60 characters: letters, digits, dots, dashes or underscores, "
                            + "starting with a letter or digit.");
        }
        if (users.existsByUsernameIgnoreCase(value)) {
            throw new AdminException("Username " + value + " is already taken.");
        }
        return value;
    }

    private Collection<AppRole> resolveRoles(List<String> codes) {
        Set<String> wanted = new LinkedHashSet<>();
        wanted.add("EMPLOYEE");
        if (codes != null) {
            for (String code : codes) {
                if (StringUtils.hasText(code)) {
                    wanted.add(code.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        List<AppRole> resolved = new ArrayList<>();
        for (String code : wanted) {
            if (!ASSIGNABLE_ROLES.contains(code)) {
                throw new AdminException("Unknown role " + code + ".");
            }
            resolved.add(roles.findByCode(code)
                    .orElseThrow(() -> new AdminException("Role " + code + " is missing from the database.")));
        }
        return resolved;
    }

    private String checkedPassword(String password, String username) {
        try {
            policy.check(password, username);
        } catch (WeakPasswordException exception) {
            throw new AdminException(exception.getMessage());
        }
        return password;
    }

    private String generatePassword(String username) {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder candidate = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
            for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
                candidate.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String password = candidate.toString();
            try {
                policy.check(password, username);
                return password;
            } catch (WeakPasswordException ignored) {
                // Try again; a random password rarely fails the policy.
            }
        }
        throw new AdminException("Could not generate a temporary password. Please try again.");
    }

    /** Keeps the replaced password in the reuse history, like a self-service change. */
    private void recordHistory(AppUser user) {
        List<PasswordHistory> recent = history.findByUserIdOrderByChangedAtDesc(user.getId());
        recent.stream().skip(HISTORY_DEPTH - 1).forEach(history::delete);
        history.save(new PasswordHistory(user.getId(), user.getPasswordHash()));
    }

    private static AccountSummary toSummary(AppUser user) {
        Employee employee = user.getEmployee();
        List<String> roleCodes = user.getRoles().stream()
                .map(AppRole::getCode)
                .sorted()
                .toList();
        return new AccountSummary(
                user.getId(),
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getFullName(),
                employee.getDepartment().getCode(),
                user.getUsername(),
                user.isEnabled(),
                user.isMustChangePassword(),
                roleCodes,
                user.getLastLoginAt(),
                user.hasTwoStep(),
                user.getTwoFactorEnrolledAt());
    }
}
