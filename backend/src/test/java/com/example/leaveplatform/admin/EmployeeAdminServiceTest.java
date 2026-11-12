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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;

class EmployeeAdminServiceTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final EmployeeAdminService service = new EmployeeAdminService(employees, departments, audit);

    private final Department registrar = mock(Department.class);

    @BeforeEach
    void commonStubs() {
        when(registrar.getId()).thenReturn(3L);
        when(registrar.getCode()).thenReturn("REGISTRAR");
        when(registrar.getName()).thenReturn("Registrar Office");
        when(registrar.isActive()).thenReturn(true);
        when(departments.findById(3L)).thenReturn(Optional.of(registrar));
        when(employees.save(any(Employee.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsRecordWithOnlyNameDesignationAndDepartment() {
        EmployeeSummary summary = service.create(
                new EmployeeInput(null, "Ayesha Khan", "Assistant Registrar", 3L, null, null, null));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        Employee employee = saved.getValue();
        assertThat(employee.getFullName()).isEqualTo("Ayesha Khan");
        assertThat(employee.getDesignation()).isEqualTo("Assistant Registrar");
        assertThat(employee.getEmployeeNo()).isNull();
        assertThat(employee.getEmail()).isNull();
        assertThat(employee.getPhone()).isNull();
        assertThat(employee.getSupervisorEmployeeId()).isNull();
        assertThat(employee.isActive()).isTrue();
        assertThat(summary.departmentCode()).isEqualTo("REGISTRAR");
    }

    @Test
    void createsRecordWithEveryDetail() {
        service.create(new EmployeeInput("7021", "Bilal Ahmed", "Clerk", 3L, null,
                "Bilal@Example.edu", "0300-1234567"));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().getEmployeeNo()).isEqualTo("7021");
        assertThat(saved.getValue().getEmail()).isEqualTo("bilal@example.edu");
        assertThat(saved.getValue().getPhone()).isEqualTo("0300-1234567");
    }

    @Test
    void refusesRecordWithoutName() {
        assertThatThrownBy(() -> service.create(
                new EmployeeInput(null, "  ", "Clerk", 3L, null, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessage("Full name is required.");
        verify(employees, never()).save(any());
    }

    @Test
    void refusesRecordWithoutDepartment() {
        assertThatThrownBy(() -> service.create(
                new EmployeeInput(null, "Ayesha Khan", "Clerk", null, null, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessage("Choose a department.");
    }

    @Test
    void refusesRecordInInactiveDepartment() {
        when(registrar.isActive()).thenReturn(false);
        assertThatThrownBy(() -> service.create(
                new EmployeeInput(null, "Ayesha Khan", "Clerk", 3L, null, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void refusesStaffNumberThatBelongsToSomeoneElse() {
        Employee other = mock(Employee.class);
        when(other.getId()).thenReturn(50L);
        when(other.getFullName()).thenReturn("Someone Else");
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.create(
                new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, null, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("already belongs to Someone Else");
    }

    @Test
    void refusesMalformedEmail() {
        assertThatThrownBy(() -> service.create(
                new EmployeeInput(null, "Ayesha Khan", "Clerk", 3L, null, "not-an-address", null)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("does not look like an address");
    }

    @Test
    void refusesEmailThatBelongsToSomeoneElse() {
        Employee other = mock(Employee.class);
        when(other.getId()).thenReturn(50L);
        when(other.getFullName()).thenReturn("Someone Else");
        when(employees.findByEmailIgnoreCase("shared@example.edu")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.create(
                new EmployeeInput(null, "Ayesha Khan", "Clerk", 3L, null, "shared@example.edu", null)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("already belongs to Someone Else");
    }

    @Test
    void refusesSupervisorThatIsNotKnown() {
        when(employees.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new EmployeeInput(null, "Ayesha Khan", "Clerk", 3L, 77L, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessage("The chosen supervisor no longer exists.");
    }

    @Test
    void refusesToMakeSomeoneTheirOwnSupervisor() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(employees.findById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(50L,
                new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, 50L, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessage("An employee cannot be their own supervisor.");
    }

    @Test
    void refusesASupervisorWhoAlreadyReportsToThisEmployee() {
        Employee existing = employeeWithSupervisor(50L, null);
        employeeWithSupervisor(60L, 50L);

        assertThatThrownBy(() -> service.update(50L,
                new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, 60L, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("circle");
        verify(existing, never()).assignSupervisor(any());
    }

    @Test
    void refusesACircleOfThreeAsReadilyAsACircleOfTwo() {
        employeeWithSupervisor(50L, null);
        employeeWithSupervisor(60L, 70L);
        employeeWithSupervisor(70L, 50L);

        assertThatThrownBy(() -> service.update(50L,
                new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, 60L, null, null)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("circle");
    }

    @Test
    void allowsASupervisorWhoseOwnLineLeadsElsewhere() {
        Employee existing = employeeWithSupervisor(50L, null);
        employeeWithSupervisor(60L, 70L);
        employeeWithSupervisor(70L, null);

        service.update(50L, new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, 60L, null, null));

        verify(existing).assignSupervisor(60L);
    }

    @Test
    void stopsWalkingAReportingLineThatIsAlreadyACircleWithoutThisEmployee() {
        Employee existing = employeeWithSupervisor(50L, null);
        employeeWithSupervisor(60L, 70L);
        employeeWithSupervisor(70L, 60L);

        service.update(50L, new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, 60L, null, null));

        // A broken line elsewhere in the register is not a reason to refuse this
        // edit, and it must not send the walk round for ever.
        verify(existing).assignSupervisor(60L);
    }

    @Test
    void importRefusesASupervisorColumnThatWouldCloseACircle() {
        Employee existing = employeeWithSupervisor(50L, null);
        Employee above = employeeWithSupervisor(60L, 50L);
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));
        when(employees.findByEmployeeNo("7000")).thenReturn(Optional.of(above));

        ImportResult result = service.importCsv("7021,Ayesha Khan,Clerk,,,,7000\n");

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.messages().get(0)).contains("circle");
        verify(existing, never()).assignSupervisor(any());
    }

    private Employee employeeWithSupervisor(Long id, Long supervisorId) {
        Employee person = mock(Employee.class);
        when(person.getId()).thenReturn(id);
        when(person.getSupervisorEmployeeId()).thenReturn(supervisorId);
        when(person.getDepartment()).thenReturn(registrar);
        when(employees.findById(id)).thenReturn(Optional.of(person));
        return person;
    }

    @Test
    void updateCompletesAMissingEmailAndPhoneLater() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(existing.getEmployeeNo()).thenReturn("7021");
        when(existing.getFullName()).thenReturn("Ayesha Khan");
        when(existing.getDesignation()).thenReturn("Assistant Registrar");
        when(employees.findById(50L)).thenReturn(Optional.of(existing));
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));

        service.update(50L, new EmployeeInput("7021", "Ayesha Khan", "Assistant Registrar",
                3L, null, "ayesha@example.edu", "0300-99261261"));

        verify(existing).updateDetails("7021", "Ayesha Khan", "ayesha@example.edu",
                "0300-99261261", "Assistant Registrar");
    }

    @Test
    void updateKeepsTheSameStaffNumberWithoutCallingItADuplicate() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(existing.getEmployeeNo()).thenReturn("7021");
        when(employees.findById(50L)).thenReturn(Optional.of(existing));
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));

        service.update(50L, new EmployeeInput("7021", "Ayesha Khan", "Clerk", 3L, null, null, null));

        verify(existing).updateDetails("7021", "Ayesha Khan", null, null, "Clerk");
    }

    @Test
    void deactivateAndReactivate() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(employees.findById(50L)).thenReturn(Optional.of(existing));

        service.setActive(50L, false);
        verify(existing).deactivate();

        service.setActive(50L, true);
        verify(existing).activate();
    }

    @Test
    void importSkipsTheHeaderAndCreatesUnknownStaffNumbers() {
        when(departments.findByCode("REGISTRAR")).thenReturn(Optional.of(registrar));

        ImportResult result = service.importCsv(EmployeeAdminService.IMPORT_HEADER
                + "\n7022,Bilal Ahmed,Clerk,registrar,bilal@example.edu,0300-0000000,\n");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.messages()).isEmpty();
        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().getEmployeeNo()).isEqualTo("7022");
    }

    @Test
    void importUpdatesExistingStaffNumbers() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));
        when(departments.findByCode("REGISTRAR")).thenReturn(Optional.of(registrar));

        ImportResult result = service.importCsv(
                "7021,Ayesha Khan,Assistant Registrar,REGISTRAR,ayesha@example.edu,,\n");

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(existing).changeFullName("Ayesha Khan");
        verify(existing).changeDesignation("Assistant Registrar");
        verify(existing).changeEmail("ayesha@example.edu");
        verify(existing).moveTo(registrar);
        // The two blank columns are omissions, not instructions to erase.
        verify(existing, never()).changePhone(any());
        verify(existing, never()).assignSupervisor(any());
        verify(existing, never()).updateDetails(any(), any(), any(), any(), any());
        verify(employees, never()).save(any(Employee.class));
    }

    @Test
    void aSheetOfNumbersNamesAndDepartmentsLeavesTheRestOfTheRecordAlone() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(existing.getEmail()).thenReturn("ayesha@example.edu");
        when(existing.getPhone()).thenReturn("0300-00000000");
        when(existing.getSupervisorEmployeeId()).thenReturn(60L);
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));
        when(departments.findByCode("REGISTRAR")).thenReturn(Optional.of(registrar));

        ImportResult result = service.importCsv("7021,Ayesha Khan,Clerk,REGISTRAR,,,\n");

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        // This is the sheet HR sends every term. Writing all five columns from
        // it would empty the contact details and the reporting line of every
        // person on it, which is how the earlier import lost numbers.
        verify(existing, never()).changeEmail(any());
        verify(existing, never()).changePhone(any());
        verify(existing, never()).assignSupervisor(any());
        verify(existing, never()).updateDetails(any(), any(), any(), any(), any());
    }

    @Test
    void aBlankDepartmentColumnKeepsTheDepartmentThePersonIsAlreadyIn() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));

        ImportResult result = service.importCsv("7021,Ayesha Khan,Clerk,,,,\n");

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(existing, never()).moveTo(any());
        verify(departments, never()).findByCode(any());
    }

    @Test
    void importSetsTheSupervisorWhenTheColumnIsFilled() {
        Employee existing = mock(Employee.class);
        Employee supervisor = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(supervisor.getId()).thenReturn(60L);
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));
        when(employees.findByEmployeeNo("7000")).thenReturn(Optional.of(supervisor));
        when(employees.findById(60L)).thenReturn(Optional.of(supervisor));

        ImportResult result = service.importCsv("7021,Ayesha Khan,Clerk,,,,7000\n");

        assertThat(result.updated()).isEqualTo(1);
        verify(existing).assignSupervisor(60L);
    }

    @Test
    void importRefusesASupervisorColumnThatNamesThePersonThemselves() {
        Employee existing = mock(Employee.class);
        when(existing.getId()).thenReturn(50L);
        when(existing.getDepartment()).thenReturn(registrar);
        when(employees.findByEmployeeNo("7021")).thenReturn(Optional.of(existing));

        ImportResult result = service.importCsv("7021,Ayesha Khan,Clerk,,,,7021\n");

        assertThat(result.updated()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.messages().get(0)).contains("cannot be their own supervisor");
        verify(existing, never()).assignSupervisor(any());
    }

    @Test
    void importReportsBadRowsAndStillAppliesTheRest() {
        when(departments.findByCode("REGISTRAR")).thenReturn(Optional.of(registrar));

        ImportResult result = service.importCsv("""
                ,No Staff Number,Clerk,REGISTRAR,,,
                7023,Unknown Department,Clerk,NOWHERE,,,
                7024,Sara Ali,Clerk,REGISTRAR,,,
                """);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.messages())
                .containsExactly(
                        "Row 1: staff number is required so the row can be matched.",
                        "Row 2: department NOWHERE is not known or not active.");
    }

    @Test
    void importResolvesTheSupervisorByStaffNumber() {
        Employee supervisor = mock(Employee.class);
        when(supervisor.getId()).thenReturn(60L);
        when(supervisor.getDepartment()).thenReturn(registrar);
        when(employees.findByEmployeeNo("7000")).thenReturn(Optional.of(supervisor));
        when(employees.findById(60L)).thenReturn(Optional.of(supervisor));
        when(departments.findByCode("REGISTRAR")).thenReturn(Optional.of(registrar));

        ImportResult result = service.importCsv("7025,Sara Ali,Clerk,REGISTRAR,,,7000\n");

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertThat(saved.getValue().getSupervisorEmployeeId()).isEqualTo(60L);
    }

    @Test
    void importRefusesEmptyText() {
        assertThatThrownBy(() -> service.importCsv("  \n "))
                .isInstanceOf(AdminException.class)
                .hasMessage("Paste the CSV rows first.");
    }

    @Test
    void importRefusesADepartmentThatIsInactive() {
        Department closed = mock(Department.class);
        when(closed.getCode()).thenReturn("OLD");
        when(closed.isActive()).thenReturn(false);
        when(departments.findByCode("OLD")).thenReturn(Optional.of(closed));

        ImportResult result = service.importCsv("7026,Sara Ali,Clerk,OLD,,,\n");

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.messages().get(0)).contains("OLD is not known or not active.");
    }

    @Test
    void searchPassesThePatternDepartmentAndLimitThrough() {
        Employee employee = mock(Employee.class);
        when(employee.getDepartment()).thenReturn(registrar);
        when(employee.getFullName()).thenReturn("Ayesha Khan");
        when(employees.search(eq(3L), eq("%ayesha%"), any(Pageable.class))).thenReturn(List.of(employee));

        List<EmployeeSummary> found = service.search(" Ayesha ", 3L);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).fullName()).isEqualTo("Ayesha Khan");
        ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
        verify(employees).search(eq(3L), eq("%ayesha%"), limit.capture());
        assertThat(limit.getValue().getPageNumber()).isZero();
        assertThat(limit.getValue().getPageSize()).isEqualTo(EmployeeAdminService.SEARCH_LIMIT);
    }

    @Test
    void searchWithoutFiltersStillAsksForOnlyTheFirstPage() {
        when(employees.search(isNull(), isNull(), any(Pageable.class))).thenReturn(List.of());

        assertThat(service.search("  ", null)).isEmpty();
        verify(employees).search(isNull(), isNull(), any(Pageable.class));
    }
}
