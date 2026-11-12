package com.example.leaveplatform.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;

/**
 * Creates the first central administrator so a fresh database can be used.
 *
 * <p>Runs only when {@code LEAVE_BOOTSTRAP_ADMIN_USERNAME} and
 * {@code LEAVE_BOOTSTRAP_ADMIN_PASSWORD} are set and no account exists yet. The
 * account must change its password at first sign-in. Remove those variables
 * from the environment once the account exists; the runner then does nothing.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private static final String ROLE = "CENTRAL_ADMIN";
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final AppRoleRepository roles;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;

    private final String username;
    private final String password;
    private final String email;
    private final String employeeNo;
    private final String fullName;
    private final String designation;
    private final String departmentCode;

    public BootstrapAdmin(Environment environment, AppUserRepository users, EmployeeRepository employees,
            AppRoleRepository roles, DepartmentRepository departments, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.employees = employees;
        this.roles = roles;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.username = environment.getProperty("app.bootstrap.username", "");
        this.password = environment.getProperty("app.bootstrap.password", "");
        this.email = environment.getProperty("app.bootstrap.email", "");
        this.employeeNo = environment.getProperty("app.bootstrap.employee-no", "ADMIN-001");
        this.fullName = environment.getProperty("app.bootstrap.full-name", "Initial Administrator");
        this.designation = environment.getProperty("app.bootstrap.designation", "Administrator");
        this.departmentCode = environment.getProperty("app.bootstrap.department-code", "");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(username) && !StringUtils.hasText(password)) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(email)
                || !StringUtils.hasText(departmentCode)) {
            throw new IllegalStateException("Bootstrap admin needs LEAVE_BOOTSTRAP_ADMIN_USERNAME, "
                    + "LEAVE_BOOTSTRAP_ADMIN_PASSWORD, LEAVE_BOOTSTRAP_ADMIN_EMAIL and "
                    + "LEAVE_BOOTSTRAP_ADMIN_DEPARTMENT; set all four or none.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.equalsIgnoreCase(username)) {
            throw new IllegalStateException("Bootstrap admin password must be at least "
                    + MIN_PASSWORD_LENGTH + " characters and must not repeat the username.");
        }
        if (users.count() > 0) {
            log.info("Bootstrap administrator not created: {} account(s) already exist.", users.count());
            return;
        }

        Department department = departments.findByCode(departmentCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Bootstrap admin department " + departmentCode + " does not exist. Use a code from"
                                + " the organization V006 loads, such as ADMIN; the SAMPLE- rows of V002 are"
                                + " deleted by it."));
        AppRole role = roles.findByCode(ROLE)
                .orElseThrow(() -> new IllegalStateException("Role " + ROLE + " is missing. Check V004 seed data."));

        Employee employee = employees.save(
                new Employee(employeeNo, fullName, email, null, designation, department));
        AppUser user = new AppUser(employee, username, passwordEncoder.encode(password), true);
        user.addRole(role);
        users.save(user);

        log.warn("Bootstrap administrator '{}' created. Remove LEAVE_BOOTSTRAP_ADMIN_* from the environment.",
                username);
    }
}
