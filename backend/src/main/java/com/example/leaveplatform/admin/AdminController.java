package com.example.leaveplatform.admin;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration endpoints. Only the central administrator may call them; the
 * rule is in {@code SecurityConfig}. The Vaadin screen uses the same services.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    public record FacultyRequest(String code, String name) {
    }

    public record DepartmentRequest(Long facultyId, String code, String name, String type) {
    }

    public record ActiveRequest(boolean active) {
    }

    public record ImportRequest(String csv) {
    }

    public record ResetPasswordRequest(String temporaryPassword) {
    }

    public record RolesRequest(List<String> roles) {
    }

    public record HolidayRequest(LocalDate date, String name) {
    }

    public record BalanceRequest(String leaveTypeCode, int year, int allocated, String reason) {
    }

    private final OrganizationAdminService organization;
    private final EmployeeAdminService employees;
    private final AccountAdminService accounts;
    private final HolidayAdminService holidays;
    private final BalanceAdminService balances;

    public AdminController(OrganizationAdminService organization, EmployeeAdminService employees,
            AccountAdminService accounts, HolidayAdminService holidays, BalanceAdminService balances) {
        this.organization = organization;
        this.employees = employees;
        this.accounts = accounts;
        this.holidays = holidays;
        this.balances = balances;
    }

    @GetMapping("/faculties")
    List<FacultySummary> faculties() {
        return organization.listFaculties();
    }

    @PostMapping("/faculties")
    @ResponseStatus(HttpStatus.CREATED)
    FacultySummary createFaculty(@RequestBody FacultyRequest request) {
        return organization.createFaculty(request.code(), request.name());
    }

    @PutMapping("/faculties/{id}/active")
    FacultySummary facultyActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return organization.setFacultyActive(id, request.active());
    }

    @GetMapping("/departments")
    List<DepartmentSummary> departments() {
        return organization.listDepartments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentSummary createDepartment(@RequestBody DepartmentRequest request) {
        return organization.createDepartment(request.facultyId(), request.code(), request.name(), request.type());
    }

    @PutMapping("/departments/{id}/active")
    DepartmentSummary departmentActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return organization.setDepartmentActive(id, request.active());
    }

    @GetMapping("/employees")
    List<EmployeeSummary> employees(@RequestParam(required = false) String query,
            @RequestParam(required = false) Long departmentId) {
        return employees.search(query, departmentId);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    EmployeeSummary createEmployee(@RequestBody EmployeeInput input) {
        return employees.create(input);
    }

    @PutMapping("/employees/{id}")
    EmployeeSummary updateEmployee(@PathVariable Long id, @RequestBody EmployeeInput input) {
        return employees.update(id, input);
    }

    @PutMapping("/employees/{id}/active")
    EmployeeSummary employeeActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return employees.setActive(id, request.active());
    }

    @PostMapping("/employees/import")
    ImportResult importEmployees(@RequestBody ImportRequest request) {
        return employees.importCsv(request.csv());
    }

    @GetMapping("/accounts")
    List<AccountSummary> accounts(@RequestParam(required = false) String query) {
        return accounts.search(query);
    }

    @GetMapping("/accounts/candidates")
    List<AccountCandidate> accountCandidates() {
        return accounts.candidates();
    }

    @GetMapping("/accounts/roles")
    List<String> accountRoles() {
        return accounts.assignableRoles();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedCredentials createAccount(@RequestBody AccountInput input, Principal principal) {
        return accounts.create(input, principal.getName());
    }

    @PostMapping("/accounts/{id}/password")
    IssuedCredentials resetAccountPassword(@PathVariable Long id,
            @RequestBody(required = false) ResetPasswordRequest request, Principal principal) {
        return accounts.resetPassword(id, request == null ? null : request.temporaryPassword(),
                principal.getName());
    }

    @PutMapping("/accounts/{id}/enabled")
    AccountSummary accountEnabled(@PathVariable Long id, @RequestBody ActiveRequest request,
            Principal principal) {
        return accounts.setEnabled(id, request.active(), principal.getName());
    }

    @PutMapping("/accounts/{id}/roles")
    AccountSummary accountRoles(@PathVariable Long id, @RequestBody RolesRequest request,
            Principal principal) {
        return accounts.replaceRoles(id, request.roles(), principal.getName());
    }

    /**
     * The office's way back for somebody who cannot sign in any more: their
     * phone, or the platform's key, has gone. There is no matching "turn it on"
     * - only the person holding the app can enrol it.
     */
    @DeleteMapping("/accounts/{id}/two-step")
    AccountSummary clearAccountTwoStep(@PathVariable Long id, Principal principal) {
        return accounts.clearTwoStep(id, principal.getName());
    }

    @GetMapping("/holidays")
    List<HolidaySummary> holidays(@RequestParam int year) {
        return holidays.list(year);
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    HolidaySummary addHoliday(@RequestBody HolidayRequest request) {
        return holidays.add(request.date(), request.name());
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeHoliday(@PathVariable Long id) {
        holidays.remove(id);
    }

    @GetMapping("/employees/{id}/balances")
    List<BalanceSummary> balances(@PathVariable Long id) {
        return balances.forEmployee(id);
    }

    @PutMapping("/employees/{id}/balances")
    BalanceSummary adjustBalance(@PathVariable Long id, @RequestBody BalanceRequest request) {
        return balances.adjust(id, request.leaveTypeCode(), request.year(), request.allocated(),
                request.reason());
    }
}
