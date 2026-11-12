package com.example.leaveplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;
import com.example.leaveplatform.organization.DepartmentType;
import com.example.leaveplatform.organization.Faculty;
import com.example.leaveplatform.organization.FacultyRepository;

class OrganizationAdminServiceTest {

    private final FacultyRepository faculties = mock(FacultyRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final OrganizationAdminService service = new OrganizationAdminService(faculties, departments, audit);

    private final Faculty faculty = mock(Faculty.class);

    @BeforeEach
    void commonStubs() {
        when(faculty.getId()).thenReturn(1L);
        when(faculty.getCode()).thenReturn("FOE");
        when(faculty.getName()).thenReturn("Faculty of Engineering");
        when(faculty.isActive()).thenReturn(true);
        when(faculties.findById(1L)).thenReturn(Optional.of(faculty));
        when(faculties.save(any(Faculty.class))).thenAnswer(call -> call.getArgument(0));
        when(departments.save(any(Department.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsAFacultyWithNormalisedCode() {
        FacultySummary summary = service.createFaculty(" foe ", "Faculty of Engineering");

        ArgumentCaptor<Faculty> saved = ArgumentCaptor.forClass(Faculty.class);
        verify(faculties).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("FOE");
        assertThat(saved.getValue().getName()).isEqualTo("Faculty of Engineering");
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(summary.code()).isEqualTo("FOE");
    }

    @Test
    void refusesFacultyCodeThatIsTooShort() {
        assertThatThrownBy(() -> service.createFaculty("A", "Faculty of Engineering"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Faculty code must be 2-16 letters, digits or hyphens.");
        verify(faculties, never()).save(any());
    }

    @Test
    void refusesFacultyCodeThatIsAlreadyUsed() {
        when(faculties.existsByCode("FOE")).thenReturn(true);

        assertThatThrownBy(() -> service.createFaculty("FOE", "Faculty of Engineering"))
                .isInstanceOf(AdminException.class)
                .hasMessage("A faculty with code FOE already exists.");
    }

    @Test
    void refusesFacultyWithoutAName() {
        assertThatThrownBy(() -> service.createFaculty("FOE", " "))
                .isInstanceOf(AdminException.class)
                .hasMessage("Faculty name is required.");
    }

    @Test
    void createsAnAdministrativeOfficeAsItsOwnDepartment() {
        DepartmentSummary summary = service.createDepartment(1L, "registrar",
                "Registrar Office", "administrative");

        ArgumentCaptor<Department> saved = ArgumentCaptor.forClass(Department.class);
        verify(departments).save(saved.capture());
        Department office = saved.getValue();
        assertThat(office.getCode()).isEqualTo("REGISTRAR");
        assertThat(office.getName()).isEqualTo("Registrar Office");
        assertThat(office.getType()).isEqualTo(DepartmentType.ADMINISTRATIVE);
        assertThat(office.getFaculty()).isSameAs(faculty);
        assertThat(summary.facultyCode()).isEqualTo("FOE");
        assertThat(summary.type()).isEqualTo(DepartmentType.ADMINISTRATIVE);
    }

    @Test
    void refusesDepartmentWithUnknownType() {
        assertThatThrownBy(() -> service.createDepartment(1L, "MEDICAL", "Medical Centre", "clinic"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Department type must be ACADEMIC, ADMINISTRATIVE or SUPPORT.");
    }

    @Test
    void refusesDepartmentCodeThatIsAlreadyUsed() {
        when(departments.existsByCode("CS")).thenReturn(true);

        assertThatThrownBy(() -> service.createDepartment(1L, "CS", "Computer Systems", "ACADEMIC"))
                .isInstanceOf(AdminException.class)
                .hasMessage("A department with code CS already exists.");
    }

    @Test
    void refusesDepartmentWhenTheFacultyIsUnknown() {
        when(faculties.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDepartment(99L, "CS", "Computer Systems", "ACADEMIC"))
                .isInstanceOf(AdminException.class)
                .hasMessage("Choose the faculty this department belongs to.");
    }

    @Test
    void deactivatesAndReactivatesAFaculty() {
        service.setFacultyActive(1L, false);
        verify(faculty).deactivate();
        verify(audit).record(eq(AuditAction.FACULTY_DEACTIVATED), eq("FACULTY"), eq(1L), any());

        // The endpoint took an active flag and then refused true, so the one
        // control that could reopen a closed faculty always answered with an error.
        service.setFacultyActive(1L, true);
        verify(faculty).activate();
        verify(audit).record(eq(AuditAction.FACULTY_ACTIVATED), eq("FACULTY"), eq(1L), any());
    }

    @Test
    void renamesAndDeactivatesADepartment() {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(7L);
        when(department.getFaculty()).thenReturn(faculty);
        when(department.getCode()).thenReturn("MEDICAL");
        when(department.getName()).thenReturn("Medical Centre");
        when(departments.findById(7L)).thenReturn(Optional.of(department));

        DepartmentSummary renamed = service.renameDepartment(7L, "University Medical Centre");
        verify(department).rename("University Medical Centre");
        assertThat(renamed.id()).isEqualTo(7L);

        service.setDepartmentActive(7L, false);
        verify(department).deactivate();

        service.setDepartmentActive(7L, true);
        verify(department).activate();
    }

    @Test
    void refusesToRenameADepartmentToNothing() {
        Department department = mock(Department.class);
        when(department.getFaculty()).thenReturn(faculty);
        when(departments.findById(7L)).thenReturn(Optional.of(department));

        assertThatThrownBy(() -> service.renameDepartment(7L, " "))
                .isInstanceOf(AdminException.class)
                .hasMessage("Department name is required.");
    }

    @Test
    void listsDepartmentsAsSummaries() {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(7L);
        when(department.getCode()).thenReturn("MEDICAL");
        when(department.getName()).thenReturn("Medical Centre");
        when(department.getFaculty()).thenReturn(faculty);
        when(department.getType()).thenReturn(DepartmentType.SUPPORT);
        when(department.isActive()).thenReturn(true);
        when(departments.findAllByOrderByNameAsc()).thenReturn(List.of(department));

        List<DepartmentSummary> listed = service.listDepartments();

        assertThat(listed).containsExactly(
                new DepartmentSummary(7L, "MEDICAL", "Medical Centre", "FOE", DepartmentType.SUPPORT, true));
    }

    @Test
    void listsFacultiesAsSummaries() {
        when(faculties.findAllByOrderByNameAsc()).thenReturn(List.of(faculty));

        assertThat(service.listFaculties())
                .containsExactly(new FacultySummary(1L, "FOE", "Faculty of Engineering", true));
    }
}
