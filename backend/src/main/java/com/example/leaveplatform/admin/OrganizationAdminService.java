package com.example.leaveplatform.admin;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;
import com.example.leaveplatform.organization.DepartmentType;
import com.example.leaveplatform.organization.Faculty;
import com.example.leaveplatform.organization.FacultyRepository;

/**
 * Creates and maintains the organisation. Departments are data, not code, so an
 * administrator can add a dean's office, the registrar, finance, procurement,
 * examinations, medical or transport without a developer.
 */
@Service
public class OrganizationAdminService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9-]{1,15}");

    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public OrganizationAdminService(FacultyRepository faculties, DepartmentRepository departments,
            AuditService audit) {
        this.faculties = faculties;
        this.departments = departments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FacultySummary> listFaculties() {
        return faculties.findAllByOrderByNameAsc().stream().map(OrganizationAdminService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentSummary> listDepartments() {
        return departments.findAllByOrderByNameAsc().stream().map(OrganizationAdminService::toSummary).toList();
    }

    @Transactional
    public FacultySummary createFaculty(String code, String name) {
        String normalized = code(code, "Faculty");
        if (faculties.existsByCode(normalized)) {
            throw new AdminException("A faculty with code " + normalized + " already exists.");
        }
        FacultySummary created = toSummary(faculties.save(new Faculty(normalized, required(name, "Faculty name"))));
        audit.record(AuditAction.FACULTY_CREATED, "FACULTY", created.id(),
                "Created faculty " + created.code() + " (" + created.name() + ").");
        return created;
    }

    @Transactional
    public FacultySummary setFacultyActive(Long id, boolean active) {
        Faculty faculty = faculties.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such faculty."));
        if (active) {
            faculty.activate();
        } else {
            faculty.deactivate();
        }
        // The departments under it keep their own flag: closing a faculty is not
        // the same decision as closing each of its units, and reopening it does
        // not reopen them.
        audit.record(active ? AuditAction.FACULTY_ACTIVATED : AuditAction.FACULTY_DEACTIVATED,
                "FACULTY", faculty.getId(),
                (active ? "Activated " : "Deactivated ") + "faculty " + faculty.getCode() + ".");
        return toSummary(faculty);
    }

    @Transactional
    public DepartmentSummary createDepartment(Long facultyId, String code, String name, String type) {
        Faculty faculty = faculties.findById(facultyId)
                .orElseThrow(() -> new AdminException("Choose the faculty this department belongs to."));
        String normalized = code(code, "Department");
        if (departments.existsByCode(normalized)) {
            throw new AdminException("A department with code " + normalized + " already exists.");
        }
        DepartmentType departmentType = type(type);
        Department department = new Department(faculty, normalized, required(name, "Department name"), departmentType);
        DepartmentSummary created = toSummary(departments.save(department));
        audit.record(AuditAction.DEPARTMENT_CREATED, "DEPARTMENT", created.id(),
                "Created " + created.type() + " unit " + created.code() + " (" + created.name() + ") under "
                        + created.facultyCode() + ".");
        return created;
    }

    @Transactional
    public DepartmentSummary renameDepartment(Long id, String name) {
        Department department = department(id);
        department.rename(required(name, "Department name"));
        audit.record(AuditAction.DEPARTMENT_RENAMED, "DEPARTMENT", department.getId(),
                "Renamed " + department.getCode() + " to " + department.getName() + ".");
        return toSummary(department);
    }

    @Transactional
    public DepartmentSummary setDepartmentActive(Long id, boolean active) {
        Department department = department(id);
        if (active) {
            department.activate();
        } else {
            department.deactivate();
        }
        audit.record(active ? AuditAction.DEPARTMENT_ACTIVATED : AuditAction.DEPARTMENT_DEACTIVATED,
                "DEPARTMENT", department.getId(),
                (active ? "Activated " : "Deactivated ") + department.getCode() + ".");
        return toSummary(department);
    }

    private Department department(Long id) {
        return departments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such department."));
    }

    private static DepartmentType type(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new AdminException("Choose a department type.");
        }
        try {
            return DepartmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AdminException("Department type must be ACADEMIC, ADMINISTRATIVE or SUPPORT.");
        }
    }

    private static String code(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new AdminException(label + " code is required.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new AdminException(label + " code must be 2-16 letters, digits or hyphens.");
        }
        return normalized;
    }

    private static String required(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new AdminException(label + " is required.");
        }
        return raw.trim();
    }

    private static FacultySummary toSummary(Faculty faculty) {
        return new FacultySummary(faculty.getId(), faculty.getCode(), faculty.getName(), faculty.isActive());
    }

    private static DepartmentSummary toSummary(Department department) {
        return new DepartmentSummary(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getFaculty().getCode(),
                department.getType(),
                department.isActive());
    }
}
