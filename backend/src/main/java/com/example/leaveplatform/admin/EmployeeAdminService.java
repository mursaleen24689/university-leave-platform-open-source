package com.example.leaveplatform.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;

/**
 * Creates and corrects employee records. Name, designation and department are
 * required; staff number, e-mail, phone and supervisor can be filled in later,
 * so a record can be entered the day the university learns of a person and
 * completed when HR supplies the rest.
 */
@Service
public class EmployeeAdminService {

    public static final String IMPORT_HEADER =
            "employee_no,full_name,designation,department_code,email,phone,supervisor_no";

    /**
     * How many people one search may list. The university is 3,000 staff, so an
     * uncapped search would load the whole register into the browser; a person
     * looking for a colleague types part of the name instead.
     */
    public static final int SEARCH_LIMIT = 50;

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public EmployeeAdminService(EmployeeRepository employees, DepartmentRepository departments, AuditService audit) {
        this.employees = employees;
        this.departments = departments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummary> search(String query, Long departmentId) {
        String pattern = StringUtils.hasText(query)
                ? "%" + query.trim().toLowerCase(Locale.ROOT) + "%"
                : null;
        return employees.search(departmentId, pattern, PageRequest.of(0, SEARCH_LIMIT)).stream()
                .map(EmployeeAdminService::toSummary)
                .toList();
    }

    @Transactional
    public EmployeeSummary create(EmployeeInput input) {
        Employee employee = new Employee(
                checkEmployeeNo(input.employeeNo(), null),
                checkRequired(input.fullName(), "Full name"),
                checkEmail(input.email(), null),
                checkPhone(input.phone()),
                checkRequired(input.designation(), "Designation"),
                activeDepartment(input.departmentId()));
        employee.assignSupervisor(supervisor(input.supervisorId(), null));
        EmployeeSummary created = toSummary(employees.save(employee));
        audit.record(AuditAction.EMPLOYEE_CREATED, "EMPLOYEE", created.id(),
                "Created employee " + describe(created) + " in " + created.departmentCode() + ".");
        return created;
    }

    @Transactional
    public EmployeeSummary update(Long id, EmployeeInput input) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such employee."));
        employee.updateDetails(
                checkEmployeeNo(input.employeeNo(), id),
                checkRequired(input.fullName(), "Full name"),
                checkEmail(input.email(), id),
                checkPhone(input.phone()),
                checkRequired(input.designation(), "Designation"));
        employee.moveTo(activeDepartment(input.departmentId()));
        employee.assignSupervisor(supervisor(input.supervisorId(), id));
        EmployeeSummary updated = toSummary(employee);
        audit.record(AuditAction.EMPLOYEE_UPDATED, "EMPLOYEE", updated.id(),
                "Updated employee " + describe(updated) + " in " + updated.departmentCode() + ".");
        return updated;
    }

    @Transactional
    public EmployeeSummary setActive(Long id, boolean active) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such employee."));
        if (active) {
            employee.activate();
        } else {
            employee.deactivate();
        }
        EmployeeSummary summary = toSummary(employee);
        audit.record(active ? AuditAction.EMPLOYEE_ACTIVATED : AuditAction.EMPLOYEE_DEACTIVATED,
                "EMPLOYEE", summary.id(), (active ? "Activated " : "Deactivated ") + describe(summary) + ".");
        return summary;
    }

    /**
     * Imports the documented CSV. Existing staff numbers are updated, new ones
     * are created, and rows with a validation problem are reported and skipped.
     *
     * <p>On a staff number that already exists, a blank cell means "leave what
     * is stored alone", not "erase it". A sheet carrying only numbers, names,
     * designations and departments is the usual way to correct those four, and
     * it must not wipe the e-mail addresses, phone numbers and reporting lines
     * somebody else spent a term collecting - a mistake made in the earlier application when an
     * approval copied every column. Clearing a field on purpose is done on the
     * employee screen, where an empty box is a decision rather than an omission.
     */
    @Transactional
    public ImportResult importCsv(String csv) {
        List<List<String>> rows = Csv.rows(csv);
        if (rows.isEmpty()) {
            throw new AdminException("Paste the CSV rows first.");
        }
        int created = 0;
        int updated = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();
        int line = 0;
        for (List<String> row : rows) {
            line++;
            if (line == 1 && isHeader(row)) {
                continue;
            }
            String staffNo = cell(row, 0);
            if (staffNo.isEmpty()) {
                failed++;
                messages.add("Row " + line + ": staff number is required so the row can be matched.");
                continue;
            }
            try {
                Optional<Employee> existing = employees.findByEmployeeNo(staffNo);
                if (existing.isPresent()) {
                    applySuppliedColumns(existing.get(), row);
                    updated++;
                } else {
                    Employee employee = new Employee(staffNo,
                            checkRequired(cell(row, 1), "full name"),
                            checkEmail(cell(row, 4), null),
                            checkPhone(cell(row, 5)),
                            checkRequired(cell(row, 2), "designation"),
                            departmentByCode(cell(row, 3)));
                    employee.assignSupervisor(supervisorId(cell(row, 6)));
                    employees.save(employee);
                    created++;
                }
            } catch (AdminException exception) {
                failed++;
                messages.add("Row " + line + ": " + exception.getMessage());
            }
        }
        audit.record(AuditAction.EMPLOYEE_IMPORTED, "EMPLOYEE", null,
                "Imported staff rows: " + created + " created, " + updated + " updated, " + failed + " skipped.");
        return new ImportResult(created, updated, failed, messages);
    }

    /** Writes only the columns the row carries; see {@link #importCsv} for why. */
    private void applySuppliedColumns(Employee employee, List<String> row) {
        String fullName = cell(row, 1);
        if (!fullName.isEmpty()) {
            employee.changeFullName(fullName);
        }
        String designation = cell(row, 2);
        if (!designation.isEmpty()) {
            employee.changeDesignation(designation);
        }
        String departmentCode = cell(row, 3);
        if (!departmentCode.isEmpty()) {
            employee.moveTo(departmentByCode(departmentCode));
        }
        String email = cell(row, 4);
        if (!email.isEmpty()) {
            employee.changeEmail(checkEmail(email, employee.getId()));
        }
        String phone = cell(row, 5);
        if (!phone.isEmpty()) {
            employee.changePhone(checkPhone(phone));
        }
        String supervisorNo = cell(row, 6);
        if (!supervisorNo.isEmpty()) {
            employee.assignSupervisor(supervisor(supervisorId(supervisorNo), employee.getId()));
        }
    }

    private Department departmentByCode(String code) {
        return departments.findByCode(code.toUpperCase(Locale.ROOT))
                .filter(Department::isActive)
                .orElseThrow(() -> new AdminException(
                        "department " + code + " is not known or not active."));
    }

    private Long supervisorId(String staffNo) {
        if (staffNo.isEmpty()) {
            return null;
        }
        return employees.findByEmployeeNo(staffNo)
                .map(Employee::getId)
                .orElseThrow(() -> new AdminException(
                        "supervisor " + staffNo + " is not a known staff number."));
    }

    private Department activeDepartment(Long id) {
        if (id == null) {
            throw new AdminException("Choose a department.");
        }
        Department department = departments.findById(id)
                .orElseThrow(() -> new AdminException("Choose a department."));
        if (!department.isActive()) {
            throw new AdminException("Department " + department.getCode() + " is not active.");
        }
        return department;
    }

    /**
     * The rule a staff number has to satisfy: up to thirty characters, and
     * somebody else's number stays somebody else's. Public because a member of
     * staff proposing a correction to their own record is held to the same rule
     * as the person typing it in here - one rule, one place, no drift.
     */
    public String checkEmployeeNo(String raw, Long selfId) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 30) {
            throw new AdminException("Staff number cannot be longer than 30 characters.");
        }
        employees.findByEmployeeNo(value).ifPresent(other -> {
            if (!other.getId().equals(selfId)) {
                throw new AdminException("Staff number " + value + " already belongs to " + other.getFullName() + ".");
            }
        });
        return value;
    }

    /** The rule an e-mail address has to satisfy; see {@link #checkEmployeeNo} for why it is public. */
    public String checkEmail(String raw, Long selfId) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 150 || !EMAIL.matcher(value).matches()) {
            throw new AdminException("E-mail " + value + " does not look like an address.");
        }
        employees.findByEmailIgnoreCase(value).ifPresent(other -> {
            if (!other.getId().equals(selfId)) {
                throw new AdminException("E-mail " + value + " already belongs to " + other.getFullName() + ".");
            }
        });
        return value;
    }

    /** The rule a phone number has to satisfy; see {@link #checkEmployeeNo} for why it is public. */
    public String checkPhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 40) {
            throw new AdminException("Phone number cannot be longer than 40 characters.");
        }
        return value;
    }

    private Long supervisor(Long supervisorId, Long selfId) {
        if (supervisorId == null) {
            return null;
        }
        if (supervisorId.equals(selfId)) {
            throw new AdminException("An employee cannot be their own supervisor.");
        }
        Employee chosen = employees.findById(supervisorId)
                .orElseThrow(() -> new AdminException("The chosen supervisor no longer exists."));
        if (selfId != null && reportsBackTo(chosen, selfId)) {
            throw new AdminException("That supervisor already reports to this employee, so the two would"
                    + " sit in a circle and approve each other's leave.");
        }
        return supervisorId;
    }

    /**
     * Whether the reporting line above {@code chosen} leads back to the employee
     * being edited. A circle of two is the obvious case, but a circle of five is
     * the same defect and just as hard to see on a screen, so the whole line is
     * walked. The set of ids already visited is what ends the walk: a line that
     * is somehow already a circle stops there instead of looping forever.
     */
    private boolean reportsBackTo(Employee chosen, Long selfId) {
        Set<Long> visited = new HashSet<>();
        Long above = chosen.getSupervisorEmployeeId();
        while (above != null && visited.add(above)) {
            if (above.equals(selfId)) {
                return true;
            }
            above = employees.findById(above).map(Employee::getSupervisorEmployeeId).orElse(null);
        }
        return false;
    }

    private static boolean isHeader(List<String> row) {
        return !row.isEmpty() && "employee_no".equalsIgnoreCase(row.get(0).trim());
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index).trim() : "";
    }

    /** The rule a field that may never be empty has to satisfy. */
    public static String checkRequired(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new AdminException(label + " is required.");
        }
        return raw.trim();
    }

    private static String describe(EmployeeSummary employee) {
        String number = employee.employeeNo() == null ? "without a staff number" : employee.employeeNo();
        return employee.fullName() + " (" + number + ")";
    }

    private static EmployeeSummary toSummary(Employee employee) {
        Department department = employee.getDepartment();
        return new EmployeeSummary(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getFullName(),
                employee.getDesignation(),
                department.getId(),
                department.getCode(),
                department.getName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getSupervisorEmployeeId(),
                employee.isActive());
    }
}
