package com.example.leaveplatform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.leave.LeaveApplication;
import com.example.leaveplatform.leave.LeaveApplicationRepository;
import com.example.leaveplatform.leave.LeaveBalance;
import com.example.leaveplatform.leave.LeaveBalanceRepository;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveStatus;
import com.example.leaveplatform.leave.LeaveType;
import com.example.leaveplatform.leave.LeaveTypeRepository;
import com.example.leaveplatform.leave.PublicHolidayRepository;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;

class ReportServiceTest {

    private static final String USERNAME = "report.reader";
    private static final Long OWN_DEPARTMENT = 1L;
    private static final Long OTHER_DEPARTMENT = 99L;
    private static final Long EMPLOYEE_ID = 10L;
    private static final Long TYPE_ID = 5L;
    private static final int YEAR = 2026;
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);
    private static final LeaveFilter ANY_PERIOD = LeaveFilter.any();

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LeaveApplicationRepository applications = mock(LeaveApplicationRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    private final PublicHolidayRepository calendar = mock(PublicHolidayRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AuditService audit = mock(AuditService.class);
    /** A server that has been given a signing key, which is how it should run. */
    private final VerificationCode verification =
            new VerificationCode(new VerificationKey("test verification key"), "");
    private final ReportService service = new ReportService(users, employees, applications, balances,
            types, calendar, departments, audit, verification);

    private final Department department = mock(Department.class);
    private final Employee employee = mock(Employee.class);
    private final LeaveType casual = mock(LeaveType.class);

    @BeforeEach
    void commonStubs() {
        when(department.getId()).thenReturn(OWN_DEPARTMENT);
        when(department.getName()).thenReturn("Computer and Information Systems Engineering");
        when(employee.getId()).thenReturn(EMPLOYEE_ID);
        when(employee.getEmployeeNo()).thenReturn("EMP-001");
        when(employee.getFullName()).thenReturn("Test Employee");
        when(employee.getDesignation()).thenReturn("Assistant Professor");
        when(employee.getDepartment()).thenReturn(department);
        when(casual.getId()).thenReturn(TYPE_ID);
        when(casual.getCode()).thenReturn(LeaveType.CASUAL);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.getAnnualAllowance()).thenReturn(8);
        when(types.findByCodeAndActiveTrue(LeaveType.CASUAL)).thenReturn(Optional.of(casual));
        when(departments.findById(OWN_DEPARTMENT)).thenReturn(Optional.of(department));
        when(employees.findByDepartmentIdAndActiveTrueOrderByFullNameAsc(OWN_DEPARTMENT))
                .thenReturn(List.of(employee));
        when(employees.findByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(employee));
    }

    @Test
    void anOrdinaryEmployeeIsRefusedBeforeAnythingIsRead() {
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.register(USERNAME, null, ANY_PERIOD))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.registerCsv(USERNAME, null, ANY_PERIOD))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.staffSummary(USERNAME, null, YEAR))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.staffSummaryCsv(USERNAME, null, YEAR))
                .isInstanceOf(ResponseStatusException.class);

        verify(applications, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(employees, never()).findByActiveTrueOrderByFullNameAsc();
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aDepartmentHeadCannotAskForAnotherDepartmentsData() {
        signIn("HOD");

        assertThatThrownBy(() -> service.registerCsv(USERNAME, OTHER_DEPARTMENT, ANY_PERIOD))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(
                        exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(applications, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void anAdministratorMayNameAnyDepartment() {
        signIn("CENTRAL_ADMIN");
        givenRegisterRows();

        service.register(USERNAME, OTHER_DEPARTMENT, ANY_PERIOD);
        verify(applications).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void theScreenReadStopsAtTheStatedCeiling() {
        signIn("HOD");
        when(applications.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of()));

        service.register(USERNAME, null, ANY_PERIOD);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(applications).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(ReportService.SCREEN_LIMIT);
    }

    @Test
    void aFileKeepsReadingUntilTheDataRunsOut() {
        signIn("HOD");
        int firstPage = ReportService.EXPORT_PAGE;
        List<LeaveApplication> full = new ArrayList<>();
        for (int index = 0; index < firstPage; index++) {
            full.add(undecided((long) index));
        }
        List<LeaveApplication> last = List.of(undecided(900L));
        when(applications.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> {
                    Pageable pageable = call.getArgument(1);
                    return page(pageable.getPageNumber() == 0 ? full : last);
                });

        CsvFile file = service.registerCsv(USERNAME, null, ANY_PERIOD);

        verify(applications, times(2)).findAll(any(Specification.class), any(Pageable.class));
        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(applications, times(2)).findAll(any(Specification.class), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getPageNumber)
                .containsExactly(0, 1);
        assertThat(pages.getAllValues()).allMatch(page ->
                page.getPageSize() == ReportService.EXPORT_PAGE);
        // header + every row, each ended by CRLF, and nothing else.
        assertThat(csvLines(file)).hasSize(firstPage + last.size() + 1);
    }

    @Test
    void aFileOrdersTheNewestRequestsFirst() {
        signIn("HOD");
        when(applications.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of()));

        service.registerCsv(USERNAME, null, ANY_PERIOD);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(applications).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "startDate")
                .and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void anExportSaysWhoTookItWhichDepartmentAndWhichFilter() {
        signIn("HOD");
        givenRegisterRows(undecided(7L));

        service.registerCsv(USERNAME, OWN_DEPARTMENT,
                new LeaveFilter(MONDAY, WEDNESDAY, LeaveStatus.APPROVED));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.REPORT_EXPORTED), eq("REPORT"), eq("leave-register"),
                detail.capture());
        assertThat(detail.getValue())
                .contains("Computer and Information Systems Engineering")
                .contains("from 2026-11-02 to 2026-11-04, approved")
                .contains("1 row(s)");
    }

    @Test
    void aWholeUniversityExportIsLabelledAsOne() {
        signIn("CENTRAL_ADMIN");
        when(applications.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of()));

        service.registerCsv(USERNAME, null, ANY_PERIOD);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.REPORT_EXPORTED), eq("REPORT"), eq("leave-register"),
                detail.capture());
        assertThat(detail.getValue()).contains("the whole university").contains("everything");
    }

    @Test
    void aSummaryExportRecordsTheYearItWasTakenFor() {
        signIn("HOD");
        when(balances.findAllForYear(TYPE_ID, YEAR, OWN_DEPARTMENT)).thenReturn(List.of());
        when(applications.totalsForYear(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31),
                OWN_DEPARTMENT)).thenReturn(List.of());

        service.staffSummaryCsv(USERNAME, null, YEAR);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.REPORT_EXPORTED), eq("REPORT"), eq("staff-summary"),
                detail.capture());
        assertThat(detail.getValue()).contains("year 2026").contains("1 employee(s)");
    }

    @Test
    void anImpossibleYearIsRefusedBeforeTheStaffListIsRead() {
        signIn("HOD");

        assertThatThrownBy(() -> service.staffSummary(USERNAME, null, 1899))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(employees, never()).findByDepartmentIdAndActiveTrueOrderByFullNameAsc(OWN_DEPARTMENT);
    }

    @Test
    void aRegisterRowCarriesWhatTheRequestAlreadyKnows() {
        signIn("HOD");
        Instant appliedAt = Instant.parse("2026-11-01T12:00:00Z");
        LeaveApplication application = mock(LeaveApplication.class);
        when(application.getId()).thenReturn(7L);
        when(application.getReference()).thenReturn("LV-000007");
        when(application.getEmployee()).thenReturn(employee);
        when(application.getLeaveType()).thenReturn(casual);
        when(application.getStartDate()).thenReturn(MONDAY);
        when(application.getEndDate()).thenReturn(WEDNESDAY);
        when(application.getDays()).thenReturn(3);
        when(application.getStatus()).thenReturn(LeaveStatus.APPROVED);
        when(application.isCompensated()).thenReturn(true);
        when(application.getReason()).thenReturn("Family event");
        when(application.getRemarks()).thenReturn("Approved as compensatory.");
        when(application.getCreatedAt()).thenReturn(appliedAt);
        when(application.getDecidedAt()).thenReturn(Instant.parse("2026-11-05T12:00:00Z"));
        givenRegisterRows(application);

        LeaveRegisterRow row = service.register(USERNAME, null, ANY_PERIOD).get(0);

        ZoneId zone = ZoneId.systemDefault();
        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.reference()).isEqualTo("LV-000007");
        assertThat(row.employeeNo()).isEqualTo("EMP-001");
        assertThat(row.department()).isEqualTo("Computer and Information Systems Engineering");
        assertThat(row.leaveType()).isEqualTo(LeaveType.CASUAL);
        assertThat(row.days()).isEqualTo(3);
        assertThat(row.statusLabel()).isEqualTo("Approved");
        assertThat(row.compensatory()).isTrue();
        assertThat(row.appliedOn()).isEqualTo(appliedAt.atZone(zone).toLocalDate());
        assertThat(row.decidedOn()).isEqualTo(Instant.parse("2026-11-05T12:00:00Z")
                .atZone(zone).toLocalDate());
    }

    @Test
    void anUndecidedRequestHasNoDecisionDateAndNothingIsInvented() {
        signIn("HOD");
        givenRegisterRows(undecided(8L));

        LeaveRegisterRow row = service.register(USERNAME, null, ANY_PERIOD).get(0);

        assertThat(row.decidedOn()).isNull();
        assertThat(row.compensatory()).isFalse();
        assertThat(row.statusLabel()).isEqualTo("Waiting for HOD");
    }

    @Test
    void aMissingStaffNumberBecomesAnEmptyCellNotTheWordNull() {
        signIn("HOD");
        when(employee.getEmployeeNo()).thenReturn(null);
        givenRegisterRows(undecided(9L));

        CsvFile file = service.registerCsv(USERNAME, null, ANY_PERIOD);

        String dataLine = csvLines(file).get(1);
        assertThat(dataLine).doesNotContain("null");
        assertThat(dataLine.split(",", -1)[2]).isEmpty();
    }

    @Test
    void theRegisterQuotesTheNumberThePaperCarries() {
        signIn("HOD");
        givenRegisterRows(undecided(123L));

        List<String> lines = csvLines(service.registerCsv(USERNAME, null, ANY_PERIOD));

        // An office matching a printed form to the register reads the same
        // number off both, rather than working out which row is 123.
        assertThat(lines.get(0)).startsWith("No,Reference,Staff No,Name,");
        assertThat(lines.get(1).split(",", -1)[1]).isEqualTo("LV-000123");
    }

    @Test
    void anEmployeeWithNoBalanceRowStillShowsTheirAllowance() {
        signIn("HOD");
        when(balances.findAllForYear(TYPE_ID, YEAR, OWN_DEPARTMENT)).thenReturn(List.of());
        when(applications.totalsForYear(any(), any(), any())).thenReturn(List.of());

        StaffSummaryRow row = service.staffSummary(USERNAME, null, YEAR).get(0);

        assertThat(row.allocated()).isEqualTo(8);
        assertThat(row.used()).isZero();
        assertThat(row.remaining()).isEqualTo(8);
        assertThat(row.approvedDays()).isZero();
        assertThat(row.requests()).isZero();
    }

    @Test
    void aSummaryAddsTheDaysFromEveryWaitingRequestOfTheYear() {
        signIn("HOD");
        when(balances.findAllForYear(TYPE_ID, YEAR, OWN_DEPARTMENT)).thenReturn(
                List.of(new BalanceRow(EMPLOYEE_ID, 8, 3)));
        when(applications.totalsForYear(any(), any(), any())).thenReturn(List.of(
                new Totals(EMPLOYEE_ID, LeaveStatus.APPROVED, 2L, 1L),
                new Totals(EMPLOYEE_ID, LeaveStatus.PENDING, 1L, 1L),
                new Totals(EMPLOYEE_ID, LeaveStatus.PENDING_SUPERVISOR, 2L, 1L),
                new Totals(EMPLOYEE_ID, LeaveStatus.REJECTED, 4L, 1L)));

        StaffSummaryRow row = service.staffSummary(USERNAME, null, YEAR).get(0);

        assertThat(row.allocated()).isEqualTo(8);
        assertThat(row.used()).isEqualTo(3);
        assertThat(row.remaining()).isEqualTo(5);
        assertThat(row.approvedDays()).isEqualTo(2);
        // rejected and withdrawn days are not waiting, so they are left out of
        // that figure but still counted as requests.
        assertThat(row.waitingDays()).isEqualTo(3);
        assertThat(row.requests()).isEqualTo(4);
    }

    @Test
    void aSheetMarksTheWeekendTheHolidayAndTheApprovedDaysOfTheMonth() {
        signIn("HOD");
        when(calendar.findDatesBetween(LocalDate.of(YEAR, 11, 1), LocalDate.of(YEAR, 11, 30)))
                .thenReturn(List.of(LocalDate.of(YEAR, 11, 3)));
        givenMonthRows(marked(11L, LeaveStatus.APPROVED, false));

        AttendanceRow row = service.attendanceSheet(USERNAME, null, YEAR, 11).rows().get(0);

        assertThat(row.marks()).hasSize(30);
        // Sunday the 1st, Monday the 2nd, Tuesday the 3rd is a university holiday,
        // Wednesday the 4th, Thursday the 5th has nothing recorded for it.
        assertThat(row.marks().get(0)).isEqualTo(AttendanceSheet.WEEKEND);
        assertThat(row.marks().get(1)).isEqualTo(LeaveType.CASUAL);
        assertThat(row.marks().get(2)).isEqualTo(AttendanceSheet.HOLIDAY);
        assertThat(row.marks().get(3)).isEqualTo(LeaveType.CASUAL);
        assertThat(row.marks().get(4)).isEmpty();
        assertThat(row.approvedDays()).isEqualTo(2);
        assertThat(row.waitingDays()).isZero();
        assertThat(row.allocated()).isEqualTo(8);
    }

    @Test
    void aRequestNobodyHasDecidedIsMarkedAsWaitingAndNotAsLeave() {
        signIn("HOD");
        givenMonthRows(marked(12L, LeaveStatus.PENDING, false));

        AttendanceRow row = service.attendanceSheet(USERNAME, null, YEAR, 11).rows().get(0);

        assertThat(row.marks().get(1)).isEqualTo(AttendanceSheet.WAITING);
        assertThat(row.marks().get(3)).isEqualTo(AttendanceSheet.WAITING);
        assertThat(row.approvedDays()).isZero();
        assertThat(row.waitingDays()).isEqualTo(3);
    }

    @Test
    void anApprovedCompensatoryDayCarriesAStar() {
        signIn("HOD");
        givenMonthRows(marked(13L, LeaveStatus.APPROVED, true));

        AttendanceRow row = service.attendanceSheet(USERNAME, null, YEAR, 11).rows().get(0);

        assertThat(row.marks().get(1)).isEqualTo(LeaveType.CASUAL + "*");
    }

    @Test
    void aFebruarySheetHasTheDaysThatFebruaryActuallyHas() {
        signIn("HOD");

        AttendanceSheet sheet = service.attendanceSheet(USERNAME, null, YEAR, 2);

        assertThat(sheet.days()).isEqualTo(28);
        assertThat(sheet.firstDay()).isEqualTo(LocalDate.of(YEAR, 2, 1));
        assertThat(sheet.lastDay()).isEqualTo(LocalDate.of(YEAR, 2, 28));
        assertThat(sheet.label()).isEqualTo("February 2026");
    }

    @Test
    void anImpossibleMonthIsRefusedBeforeTheStaffListIsRead() {
        signIn("HOD");

        assertThatThrownBy(() -> service.attendanceSheet(USERNAME, null, YEAR, 13))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(employees, never()).findByDepartmentIdAndActiveTrueOrderByFullNameAsc(OWN_DEPARTMENT);
    }

    @Test
    void anAttendanceFileHasOneColumnPerDayAndIsAuditedWithItsMonth() {
        signIn("HOD");
        givenMonthRows(marked(14L, LeaveStatus.APPROVED, false));

        CsvFile file = service.attendanceCsv(USERNAME, null, YEAR, 11);

        assertThat(file.fileName()).isEqualTo("app-attendance-2026-11.csv");
        List<String> lines = csvLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).startsWith("Staff No,Name,Department,Designation,1,2,3")
                .endsWith("Approved Days,Waiting Days,Granted,Used,Left");
        // four describing columns, one per day of November, five totals.
        assertThat(lines.get(1).split(",", -1)).hasSize(4 + 30 + 5);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.REPORT_EXPORTED), eq("REPORT"), eq("attendance-sheet"),
                detail.capture());
        assertThat(detail.getValue()).contains("November 2026").contains("1 employee(s)");
    }

    @Test
    void aPrintRunNeedsAtLeastOneRequest() {
        signIn("HOD");

        assertThatThrownBy(() -> service.isoForms(USERNAME, List.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.isoForms(USERNAME, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(applications, never()).findById(any());
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aPrintRunIsAPileOfPaperNotAWholeYear() {
        signIn("CENTRAL_ADMIN");
        List<Long> tooMany = new ArrayList<>();
        for (long id = 1; id <= ReportService.PRINT_LIMIT + 1L; id++) {
            tooMany.add(id);
        }

        assertThatThrownBy(() -> service.isoForms(USERNAME, tooMany))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(
                        exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(applications, never()).findById(any());
    }

    @Test
    void aDepartmentHeadCannotPrintAnotherDepartmentsForm() {
        Department other = mock(Department.class);
        Employee outsider = mock(Employee.class);
        when(other.getId()).thenReturn(OTHER_DEPARTMENT);
        when(outsider.getDepartment()).thenReturn(other);
        LeaveApplication elsewhere = printable(113L, LeaveStatus.APPROVED);
        when(elsewhere.getEmployee()).thenReturn(outsider);
        signIn("HOD");
        givenFormRows(printable(111L, LeaveStatus.APPROVED), elsewhere);

        assertThatThrownBy(() -> service.isoForms(USERNAME, List.of(111L, 113L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        // The whole run is refused. A page that stopped halfway would have put
        // one department's paper in another's hands and left no record behind.
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aFormIsRefusedForANumberThatIsNotOnTheRegister() {
        signIn("CENTRAL_ADMIN");
        when(applications.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.isoForms(USERNAME, List.of(999L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(
                        exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aPrintRunIsAuditedWithEveryNumberItPutOnPaper() {
        signIn("CENTRAL_ADMIN");
        givenFormRows(printable(111L, LeaveStatus.APPROVED), printable(112L, LeaveStatus.PENDING));

        String page = service.isoForms(USERNAME, List.of(111L, 112L));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.LEAVE_FORM_PRINTED), eq("LEAVE_APPLICATION"),
                eq("LV-000111"), detail.capture());
        assertThat(detail.getValue()).isEqualTo("Printed 2 application form(s): LV-000111, LV-000112.");
        assertThat(page).contains("2 application forms, 1 sheet of A4 sideways.");
    }

    @Test
    void aPrintedFormCarriesTheCodeThisServerMintsForItsNumber() {
        signIn("CENTRAL_ADMIN");
        givenFormRows(printable(111L, LeaveStatus.APPROVED));

        String page = service.isoForms(USERNAME, List.of(111L));

        assertThat(page).contains("F/SOP/REG 01/21/02").contains("LV-000111")
                .contains(verification.codeFor("LV-000111")).contains("<svg");
    }

    @Test
    void aFormPrintsItsNumberEvenBeforeTheDatabaseHandsTheColumnBack() {
        signIn("CENTRAL_ADMIN");
        LeaveApplication justSaved = printable(114L, LeaveStatus.PENDING);
        // The insert trigger has written the column but this row has not read it
        // back yet, so the first ask is empty and the display copy is stamped.
        when(justSaved.getReference()).thenReturn(null, "LV-000114");
        givenFormRows(justSaved);

        String page = service.isoForms(USERNAME, List.of(114L));

        assertThat(page).contains("LV-000114");
        verify(justSaved).stampReference();
    }

    @Test
    void aServerWithoutASigningKeyPrintsFormsThatSayTheyCannotBeChecked() {
        signIn("CENTRAL_ADMIN");
        ReportService unkeyed = new ReportService(users, employees, applications, balances, types,
                calendar, departments, audit, new VerificationCode(new VerificationKey(""), ""));
        givenFormRows(printable(111L, LeaveStatus.APPROVED));

        String page = unkeyed.isoForms(USERNAME, List.of(111L));

        assertThat(page).doesNotContain("<svg")
                .contains("This server issues no verification codes.");

        assertThatThrownBy(() -> unkeyed.verify("LV-000111", "0123456789ab"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(applications, never()).findByReference(any());
    }

    @Test
    void thePriorDaysOnThePaperExcludeTheRequestBeingPrinted() {
        signIn("CENTRAL_ADMIN");
        when(applications.daysInYear(EMPLOYEE_ID, LeaveStatus.APPROVED, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), 111L)).thenReturn(3);
        givenFormRows(printable(111L, LeaveStatus.APPROVED));

        String page = service.isoForms(USERNAME, List.of(111L));

        assertThat(page).contains("Leave availed previously: 3 days.");
    }

    @Test
    void aDepartmentHeadsOwnFormAsksTheDeanToSanctionIt() {
        signIn("CENTRAL_ADMIN");
        AppUser head = mock(AppUser.class);
        Set<AppRole> headRoles = Set.of(role("HOD"));
        when(head.getRoles()).thenReturn(headRoles);
        when(head.getEmployee()).thenReturn(employee);
        when(users.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(head));
        givenFormRows(printable(111L, LeaveStatus.PENDING));

        String page = service.isoForms(USERNAME, List.of(111L));

        assertThat(page).contains("HOD &amp; CHAIRMAN").contains("Dean CPE")
                .doesNotContain("Chairman / Head of Department");
    }

    @Test
    void aFormNamesTheOfficeFromTheirStaffRecords() {
        signIn("CENTRAL_ADMIN");
        Employee supervisor = mock(Employee.class);
        Employee chairman = mock(Employee.class);
        when(supervisor.getFullName()).thenReturn("Bilal Ahmed");
        when(chairman.getFullName()).thenReturn("Aisha Rahman");
        when(employees.findById(30L)).thenReturn(Optional.of(supervisor));
        when(employees.findById(31L)).thenReturn(Optional.of(chairman));
        LeaveApplication decided = printable(116L, LeaveStatus.APPROVED);
        when(decided.getSupervisorEmployeeId()).thenReturn(30L);
        when(decided.getSupervisorDecidedAt()).thenReturn(Instant.parse("2026-11-04T12:00:00Z"));
        when(decided.getDecidedByEmployeeId()).thenReturn(31L);
        when(decided.getDecidedAt()).thenReturn(Instant.parse("2026-11-05T12:00:00Z"));
        givenFormRows(decided);

        String page = service.isoForms(USERNAME, List.of(116L));

        assertThat(page).contains("Recommended by: Bilal Ahmed").contains("Decided by: Aisha Rahman")
                .contains("on 4 Nov 2026").contains("on 5 Nov 2026");
    }

    @Test
    void aCompensatoryFormSaysNoBalanceDaysWereSpent() {
        signIn("CENTRAL_ADMIN");
        LeaveApplication earned = printable(117L, LeaveStatus.APPROVED);
        when(earned.isCompensated()).thenReturn(true);
        givenFormRows(earned);

        String page = service.isoForms(USERNAME, List.of(117L));

        assertThat(page).contains("Compensatory - against duty on an off day.");
    }

    @Test
    void aCheckedFormAnswersWithTheRecordAsItStandsToday() {
        LeaveApplication found = printable(111L, LeaveStatus.APPROVED);
        when(found.getRemarks()).thenReturn("Approved by the chairman.");
        when(applications.findByReference("LV-000111")).thenReturn(Optional.of(found));

        ReportService.LeaveVerification answer =
                service.verify("lv-000111", verification.codeFor("LV-000111"));

        assertThat(answer.reference()).isEqualTo("LV-000111");
        assertThat(answer.applicantName()).isEqualTo("Test Employee");
        assertThat(answer.designation()).isEqualTo("Assistant Professor");
        assertThat(answer.department()).isEqualTo("Computer and Information Systems Engineering");
        assertThat(answer.leaveType()).isEqualTo("Casual Leave");
        assertThat(answer.days()).isEqualTo(2);
        assertThat(answer.statusLabel()).isEqualTo("Approved");
        // A reader outside the platform is not handed the applicant's own words,
        // the office's remarks, or who decided it.
        assertThat(answer.toString()).doesNotContain("Family event")
                .doesNotContain("Approved by the chairman.");
    }

    @Test
    void aNumberThatIsNotThereAndACodeThatIsNotRightRefuseIdentically() {
        LeaveApplication found = printable(111L, LeaveStatus.APPROVED);
        when(applications.findByReference("LV-000111")).thenReturn(Optional.of(found));

        List<ResponseStatusException> refusals = new ArrayList<>();
        refusals.add(refusedBy(() -> service.verify("LV-999999", verification.codeFor("LV-999999"))));
        refusals.add(refusedBy(() -> service.verify("LV-000111", "000000000000")));
        refusals.add(refusedBy(() -> service.verify("LV-1", "000000000000")));
        refusals.add(refusedBy(() -> service.verify("MRSALEEN-1", "000000000000")));
        refusals.add(refusedBy(() -> service.verify(null, null)));

        for (ResponseStatusException refusal : refusals) {
            assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(refusal.getReason())
                    .isEqualTo("That number and code do not go together, so nothing is confirmed.");
        }
        // A code that was not minted here, and a number the platform never
        // writes, stop before the request is read: no probe costs a query.
        verify(applications, never()).findByReference("LV-1");
        verify(applications, never()).findByReference("MRSALEEN-1");
        verify(applications, never()).findByReference("lv-000111");
    }

    /**
     * What a register read finds. The rows are built by the caller before this
     * is called: a mock made while the read was still being stubbed confuses
     * Mockito, which then reports the stubbing as unfinished.
     */
    private void givenRegisterRows(LeaveApplication... found) {
        when(applications.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(List.of(found)));
    }

    @Test
    void aRecordRunNeedsAtLeastOneName() {
        signIn("HOD");

        assertThatThrownBy(() -> service.employeeRecords(USERNAME, List.of(), YEAR))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.employeeRecords(USERNAME, null, YEAR))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(employees, never()).findById(any());
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aRecordRunIsAlsoAPileOfPaperNotEveryNameInTheUniversity() {
        signIn("CENTRAL_ADMIN");
        List<Long> tooMany = new ArrayList<>();
        for (long id = 1; id <= ReportService.PRINT_LIMIT + 1L; id++) {
            tooMany.add(id);
        }

        assertThatThrownBy(() -> service.employeeRecords(USERNAME, tooMany, YEAR))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(
                        exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(employees, never()).findById(any());
    }

    @Test
    void aRecordIsRefusedForAnEmployeeWhoDoesNotExist() {
        signIn("CENTRAL_ADMIN");
        when(employees.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.employeeRecords(USERNAME, List.of(999L), YEAR))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(
                        exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aDepartmentHeadCannotPrintAnotherDepartmentsRecord() {
        Department other = mock(Department.class);
        Employee outsider = mock(Employee.class);
        when(other.getId()).thenReturn(OTHER_DEPARTMENT);
        when(outsider.getDepartment()).thenReturn(other);
        signIn("HOD");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employees.findById(222L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID, 222L), YEAR))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        // Refused whole, before a single request was read: one name of another
        // department in the pile is the reason the run is refused, not a row to
        // drop - and the first name's year must not have been printed on the way
        // to finding it.
        verify(applications, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void anOrdinaryEmployeeIsRefusedARecordBeforeAnyoneIsRead() {
        signIn("EMPLOYEE");

        assertThatThrownBy(() -> service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID), YEAR))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(employees, never()).findById(any());
        verify(applications, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void aRecordRefusesAnImpossibleYearBeforeReadingAnyone() {
        signIn("CENTRAL_ADMIN");

        assertThatThrownBy(() -> service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID), 1899))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(
                        exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(employees, never()).findById(any());
    }

    @Test
    void aRecordRunIsAuditedWithEveryNameItPutOnPaper() {
        Employee second = mock(Employee.class);
        when(second.getId()).thenReturn(20L);
        when(second.getFullName()).thenReturn("Second Employee");
        when(second.getDepartment()).thenReturn(department);
        signIn("CENTRAL_ADMIN");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employees.findById(20L)).thenReturn(Optional.of(second));
        givenRegisterRows(printable(111L, LeaveStatus.APPROVED));

        service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID, 20L), YEAR);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.LEAVE_RECORD_PRINTED), eq("EMPLOYEE"),
                eq(String.valueOf(EMPLOYEE_ID)), detail.capture());
        assertThat(detail.getValue()).contains("2 leave record(s) for 2026")
                .contains("Test Employee").contains("Second Employee");
    }

    @Test
    void aRecordPrintsWhatThePersonAskedForAndNotWhyTheyAsked() {
        signIn("CENTRAL_ADMIN");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        givenRegisterRows(printable(111L, LeaveStatus.APPROVED),
                marked(112L, LeaveStatus.PENDING_SUPERVISOR, false));

        String page = service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID), YEAR);

        assertThat(page).contains("LEAVE RECORD &mdash; 2026").contains("LV-000111").contains("LV-000112")
                .contains("Approved").contains("Waiting for supervisor");
        // A reason is the applicant's own words about their family or their
        // health. The register and the form carry it; a sheet that goes into a
        // filing cabinet does not.
        assertThat(page).doesNotContain("Family event");
    }

    @Test
    void aRecordUsesThatYearsBalanceAndFallsBackToTheAllowance() {
        signIn("CENTRAL_ADMIN");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        givenRegisterRows();

        String withoutRow = service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID), YEAR);
        // The same rule the staff summary uses: a person with no balance row is
        // entitled to the allowance, and a sheet that said zero would say so.
        assertThat(withoutRow).contains("<b>8</b><span>days granted for 2026</span>")
                .contains("<b>0</b><span>days spent</span>")
                .contains("<b>8</b><span>days left</span>");

        LeaveBalance balance = mock(LeaveBalance.class);
        when(balance.getAllocated()).thenReturn(15);
        when(balance.getUsed()).thenReturn(4);
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        assertThat(service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID), YEAR))
                .contains("<b>15</b><span>days granted for 2026</span>")
                .contains("<b>11</b><span>days left</span>");
    }

    @Test
    void aRecordsEveryLineOfTheYearIsReadEvenPastTheScreensCeiling() {
        signIn("CENTRAL_ADMIN");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        // The screen's register stops at 200 rows; a sheet someone signs may not.
        LeaveApplication repeated = printable(1L, LeaveStatus.APPROVED);
        List<LeaveApplication> first = new ArrayList<>();
        for (int at = 0; at < ReportService.EXPORT_PAGE; at++) {
            first.add(repeated);
        }
        List<LeaveApplication> secondPage = List.of(printable(900L, LeaveStatus.APPROVED));
        when(applications.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(first))
                .thenReturn(page(secondPage));

        String page = service.employeeRecords(USERNAME, List.of(EMPLOYEE_ID), YEAR);

        assertThat(page).contains("LV-000900");
        verify(applications, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    /** What one print run does: every number the caller named, looked up once. */
    private void givenFormRows(LeaveApplication... found) {
        List<Long> ids = Arrays.stream(found).map(LeaveApplication::getId).toList();
        for (int index = 0; index < found.length; index++) {
            when(applications.findById(ids.get(index))).thenReturn(Optional.of(found[index]));
        }
    }

    /** What one month of the attendance sheet finds: every request overlapping it. */
    private void givenMonthRows(LeaveApplication... found) {
        when(applications.findAll(any(Specification.class))).thenReturn(List.of(found));
    }

    private LeaveApplication marked(Long id, LeaveStatus status, boolean compensatory) {
        LeaveApplication application = mock(LeaveApplication.class);
        when(application.getId()).thenReturn(id);
        when(application.getReference()).thenReturn("LV-%06d".formatted(id));
        when(application.getEmployee()).thenReturn(employee);
        when(application.getLeaveType()).thenReturn(casual);
        when(application.getStartDate()).thenReturn(MONDAY);
        when(application.getEndDate()).thenReturn(WEDNESDAY);
        when(application.getDays()).thenReturn(2);
        when(application.getStatus()).thenReturn(status);
        when(application.isCompensated()).thenReturn(compensatory);
        when(application.getReason()).thenReturn("Family event");
        when(application.getRemarks()).thenReturn(null);
        when(application.getCreatedAt()).thenReturn(Instant.parse("2026-11-01T12:00:00Z"));
        when(application.getDecidedAt()).thenReturn(null);
        return application;
    }

    private LeaveApplication undecided(Long id) {
        LeaveApplication application = mock(LeaveApplication.class);
        when(application.getId()).thenReturn(id);
        when(application.getReference()).thenReturn("LV-%06d".formatted(id));
        when(application.getEmployee()).thenReturn(employee);
        when(application.getLeaveType()).thenReturn(casual);
        when(application.getStartDate()).thenReturn(MONDAY);
        when(application.getEndDate()).thenReturn(WEDNESDAY);
        when(application.getDays()).thenReturn(3);
        when(application.getStatus()).thenReturn(LeaveStatus.PENDING);
        when(application.isCompensated()).thenReturn(false);
        when(application.getReason()).thenReturn("Family event");
        when(application.getRemarks()).thenReturn(null);
        when(application.getCreatedAt()).thenReturn(Instant.parse("2026-11-01T12:00:00Z"));
        when(application.getDecidedAt()).thenReturn(null);
        return application;
    }

    /**
     * A request as the printing code reads it: numbered, dated, and at one
     * stage of its life. The office names and the decision moments are left for
     * the tests that ask about them, because a printing form reads the staff
     * records they point at only when the row carries an id.
     */
    private LeaveApplication printable(Long id, LeaveStatus status) {
        LeaveApplication application = mock(LeaveApplication.class);
        when(application.getId()).thenReturn(id);
        when(application.getReference()).thenReturn("LV-%06d".formatted(id));
        when(application.getEmployee()).thenReturn(employee);
        when(application.getLeaveType()).thenReturn(casual);
        when(application.getStartDate()).thenReturn(MONDAY);
        when(application.getEndDate()).thenReturn(WEDNESDAY);
        when(application.getDays()).thenReturn(2);
        when(application.getStatus()).thenReturn(status);
        when(application.isCompensated()).thenReturn(false);
        when(application.getReason()).thenReturn("Family event");
        when(application.getCreatedAt()).thenReturn(Instant.parse("2026-11-01T12:00:00Z"));
        when(application.getDecidedAt()).thenReturn(null);
        return application;
    }

    /** A paged read answers with a Page, which is what the repository returns. */
    private static Page<LeaveApplication> page(List<LeaveApplication> rows) {
        return new PageImpl<>(rows);
    }

    /** The file as lines, so a test counts rows instead of trusting a size. */
    private static List<String> csvLines(CsvFile file) {
        String text = new String(file.content(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("﻿", "");
        return Arrays.stream(text.split("\r\n"))
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    private void signIn(String... roleCodes) {
        Set<AppRole> roles = Arrays.stream(roleCodes).map(ReportServiceTest::role)
                .collect(Collectors.toSet());
        AppUser user = mock(AppUser.class);
        when(user.getEmployee()).thenReturn(employee);
        when(user.getRoles()).thenReturn(roles);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    private static AppRole role(String code) {
        AppRole role = mock(AppRole.class);
        when(role.getCode()).thenReturn(code);
        return role;
    }

    /** The refusal a public read answers with, so a test can compare two of them. */
    private static ResponseStatusException refusedBy(Runnable call) {
        try {
            call.run();
            throw new AssertionError("Expected a refusal, and an answer instead.");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }

    /**
     * The database's grouped answer, spelled the way Spring Data reads a
     * projection: bean-style accessors, not record components.
     */
    private record Totals(Long who, LeaveStatus state, long count, long made)
            implements LeaveApplicationRepository.EmployeeStatusTotals {

        @Override
        public Long getEmployeeId() {
            return who;
        }

        @Override
        public LeaveStatus getStatus() {
            return state;
        }

        @Override
        public Long getDays() {
            return count;
        }

        @Override
        public Long getRequests() {
            return made;
        }
    }

    private record BalanceRow(Long who, int granted, int spent)
            implements LeaveBalanceRepository.YearBalance {

        @Override
        public Long getEmployeeId() {
            return who;
        }

        @Override
        public int getAllocated() {
            return granted;
        }

        @Override
        public int getUsed() {
            return spent;
        }
    }
}
