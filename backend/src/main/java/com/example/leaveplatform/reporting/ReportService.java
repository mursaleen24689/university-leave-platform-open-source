package com.example.leaveplatform.reporting;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.example.leaveplatform.leave.LeaveApplicationSpecifications;
import com.example.leaveplatform.leave.LeaveBalance;
import com.example.leaveplatform.leave.LeaveBalanceRepository;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveStatus;
import com.example.leaveplatform.leave.LeaveType;
import com.example.leaveplatform.leave.LeaveTypeRepository;
import com.example.leaveplatform.leave.PublicHolidayRepository;
import com.example.leaveplatform.organization.Department;
import com.example.leaveplatform.organization.DepartmentRepository;

/**
 * The reports a department and the administration work from: the leave register,
 * the staff summary and the monthly attendance sheet, each readable on screen and
 * downloadable as a spreadsheet file.
 *
 * <p>Two rules run through the class. A report reads the same table the screens
 * read, through the same filter, so a file cannot give a different answer to the
 * list it was downloaded from. And a report is narrowed by the caller's
 * position: an HOD sees their own department and nothing else, a central
 * administrator sees one department or the whole university, and anyone else is
 * refused before a query runs.
 */
@Service
public class ReportService {

    /** Rows the screen read returns; the same ceiling the leave lists use. */
    public static final int SCREEN_LIMIT = 200;

    /** Forms one print run carries; a pile of paper, not a whole year. */
    public static final int PRINT_LIMIT = 50;

    /**
     * How the university's paper writes a department head's designation, as the
     * the earlier application form has always written it: the head of a department signs as its
     * chairman, and their own leave is sanctioned one level up.
     */
    static final String HEAD_DESIGNATION_ON_PAPER = "HOD & CHAIRMAN";

    /**
     * Rows read at a time when building a file. An export is not cut off - a
     * sheet that quietly stops short would understate a department's year - so
     * it is paged until the data runs out instead of asking for everything at
     * once.
     */
    static final int EXPORT_PAGE = 500;

    private static final Sort REGISTER_ORDER =
            Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "id"));

    private static final Sort ENTRY_ORDER =
            Sort.by(Sort.Direction.ASC, "startDate").and(Sort.by(Sort.Direction.ASC, "id"));

    private static final List<String> REGISTER_HEADINGS =
            List.of("No", "Reference", "Staff No", "Name", "Department", "Type", "From", "To", "Days",
                    "Status", "Compensatory", "Reason", "Remarks", "Applied On", "Decided On");

    private static final List<String> SUMMARY_HEADINGS = List.of("Staff No", "Name", "Department",
            "Designation", "Year", "Allocated", "Used", "Remaining", "Approved Days", "Waiting Days",
            "Requests");

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final LeaveApplicationRepository applications;
    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final PublicHolidayRepository calendar;
    private final DepartmentRepository departments;
    private final AuditService audit;
    private final VerificationCode verification;

    public ReportService(AppUserRepository users, EmployeeRepository employees,
            LeaveApplicationRepository applications, LeaveBalanceRepository balances,
            LeaveTypeRepository types, PublicHolidayRepository calendar,
            DepartmentRepository departments, AuditService audit, VerificationCode verification) {
        this.users = users;
        this.employees = employees;
        this.applications = applications;
        this.balances = balances;
        this.types = types;
        this.calendar = calendar;
        this.departments = departments;
        this.audit = audit;
        this.verification = verification;
    }

    @Transactional(readOnly = true)
    public List<LeaveRegisterRow> register(String username, Long departmentId, LeaveFilter filter) {
        Long scope = scopeOf(username, departmentId);
        return applications.findAll(registerQuery(scope, filter),
                PageRequest.of(0, SCREEN_LIMIT, REGISTER_ORDER)).stream()
                .map(ReportService::toRegisterRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffSummaryRow> staffSummary(String username, Long departmentId, int year) {
        return buildSummary(scopeOf(username, departmentId), year);
    }

    /**
     * What a person may report on, so the screen can offer the right boxes: a
     * central administrator is offered every department and no department, an
     * HOD is told which one they are seeing and is offered no choice.
     */
    @Transactional(readOnly = true)
    public ReportScope scopeFor(String username) {
        AppUser user = account(username);
        if (hasRole(user, AppRole.CENTRAL_ADMIN)) {
            return new ReportScope(true, null);
        }
        if (hasRole(user, AppRole.HOD)) {
            Department department = user.getEmployee().getDepartment();
            return new ReportScope(false, department.getName());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only a department head or a central administrator can read these reports.");
    }

    /** Whether the caller roams the university or is fixed to one department. */
    public record ReportScope(boolean wholeUniversity, String departmentName) {
    }

    /**
     * Not read-only: writing the download into the audit trail is part of the
     * export, so that who took a spreadsheet, of which department, with which
     * filter, is still answerable afterwards.
     */
    @Transactional
    public CsvFile registerCsv(String username, Long departmentId, LeaveFilter filter) {
        Long scope = scopeOf(username, departmentId);
        List<LeaveRegisterRow> rows = allRegisterRows(scope, filter);
        exported("leave register", scope, filter.describe() + ", " + rows.size() + " row(s)");
        return CsvFile.of("app-leave-register-" + LocalDate.now() + ".csv", REGISTER_HEADINGS,
                rows.stream().map(ReportService::registerCells).toList());
    }

    @Transactional
    public CsvFile staffSummaryCsv(String username, Long departmentId, int year) {
        Long scope = scopeOf(username, departmentId);
        List<StaffSummaryRow> rows = buildSummary(scope, year);
        exported("staff summary", scope, "year " + year + ", " + rows.size() + " employee(s)");
        return CsvFile.of("app-staff-summary-" + year + ".csv", SUMMARY_HEADINGS,
                rows.stream().map(ReportService::summaryCells).toList());
    }

    /**
     * One month of the attendance sheet: every employee the caller may see, one
     * mark per day.
     */
    @Transactional(readOnly = true)
    public AttendanceSheet attendanceSheet(String username, Long departmentId, int year, int month) {
        return buildSheet(scopeOf(username, departmentId), year, month);
    }

    @Transactional
    public CsvFile attendanceCsv(String username, Long departmentId, int year, int month) {
        Long scope = scopeOf(username, departmentId);
        AttendanceSheet sheet = buildSheet(scope, year, month);
        exported("attendance sheet", scope, sheet.label() + ", " + sheet.rows().size() + " employee(s)");
        return CsvFile.of("app-attendance-" + sheet.year() + "-" + "%02d".formatted(sheet.month()) + ".csv",
                sheetHeadings(sheet), sheet.rows().stream().map(ReportService::sheetCells).toList());
    }

    /**
     * The application forms for the chosen requests, as one HTML page the
     * browser prints two to a sideways A4 sheet.
     *
     * <p>The same scope rules as every other report decide who may ask, and an
     * HOD who names a request from another department is refused rather than
     * handed a form they had no business printing: the paper carries the
     * university's form code and a code that looks official, so which requests
     * left the platform on paper is audited exactly like a spreadsheet leaving
     * it as a file.
     */
    @Transactional
    public String isoForms(String username, List<Long> applicationIds) {
        Long scope = scopeOf(username, null);
        List<IsoForm> forms = new ArrayList<>();
        for (Long id : chosen(applicationIds, "leave request")) {
            LeaveApplication application = applications.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "There is no leave request number " + id + "."));
            if (scope != null && !scope.equals(application.getEmployee().getDepartment().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A department head can only print forms for their own department.");
            }
            forms.add(toForm(application));
        }
        List<String> references = forms.stream().map(IsoForm::reference).toList();
        audit.record(AuditAction.LEAVE_FORM_PRINTED, "LEAVE_APPLICATION", references.get(0),
                "Printed %d application form(s): %s.".formatted(forms.size(),
                        String.join(", ", references)));
        return IsoFormPage.of(forms, username, LocalDate.now());
    }

    /**
     * One officer's leave record for one year, printed for the office file.
     *
     * <p>The same scope rules as every other report decide who may ask, and a
     * run is the names ticked rather than a whole department: the sheet carries
     * the university's letterhead and a signature line, so which records left
     * the platform on paper is audited exactly like a spreadsheet leaving it as
     * a file.
     *
     * <p>A record holds every request whose period touches the year, whatever
     * was decided about it, and the status column says which is which. Dropping
     * the undecided rows would hand somebody a sheet that reads as a clean
     * year, and dropping the refused ones would hide leave that was never
     * granted.
     */
    @Transactional
    public String employeeRecords(String username, List<Long> employeeIds, int year) {
        Long scope = scopeOf(username, null);
        checkYear(year);
        LeaveType casual = casualType();
        List<Employee> named = new ArrayList<>();
        for (Long id : chosen(employeeIds, "employee")) {
            Employee person = employees.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "There is no employee number " + id + "."));
            if (scope != null && !scope.equals(person.getDepartment().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A department head can only print records for their own department.");
            }
            named.add(person);
        }
        // The authority of the whole pile is settled before a single request is
        // read, so a run that ends in a refusal has not quietly printed one
        // department's year while it was looking for the name that did not belong.
        List<EmployeeRecord> records = new ArrayList<>();
        for (Employee person : named) {
            records.add(toRecord(person, casual, year));
        }
        List<String> quoted = records.stream().map(EmployeeRecord::name).toList();
        audit.record(AuditAction.LEAVE_RECORD_PRINTED, "EMPLOYEE",
                String.valueOf(employeeIds.get(0)), "Printed %d leave record(s) for %d: %s."
                        .formatted(records.size(), year, String.join(", ", quoted)));
        return EmployeeRecordPage.of(records, username, LocalDate.now());
    }

    /**
     * What a public reader is told about one request: the number, the code
     * beside it and nothing that helps anyone guess either. The answer is the
     * record as it stands today, not as the paper said it was when it was
     * printed, because a form printed before a decision is superseded by the
     * decision rather than proved by it.
     */
    @Transactional(readOnly = true)
    public LeaveVerification verify(String reference, String code) {
        if (!verification.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "This server has no LEAVE_VERIFY_KEY, so it cannot check any code.");
        }
        // A number that is not shaped like one never reaches the database, and a
        // number that is shaped like one but does not exist, or exists with a
        // code that does not match, is answered with the same refusal: none of
        // them tells a caller whether the request is real.
        if (reference == null || code == null) {
            throw refused();
        }
        // Written the way the database writes it: the trigger stamps capitals,
        // and a person typing from paper may not have left the shift key down.
        String shaped = reference.trim().toUpperCase();
        if (!verification.matches(shaped, code)) {
            throw refused();
        }
        LeaveApplication application = applications.findByReference(shaped)
                .orElseThrow(ReportService::refused);
        Employee employee = application.getEmployee();
        return new LeaveVerification(shaped, employee.getFullName(),
                employee.getDesignation(), employee.getDepartment().getName(),
                application.getLeaveType().getName(), application.getStartDate(),
                application.getEndDate(), application.getDays(), application.getStatus().label(),
                application.isCompensated());
    }

    /**
     * The refusal for a number and code that do not go together, in words that
     * help nobody: the same answer whether the request does not exist, exists
     * under another code, or was typed in a shape the platform never writes.
     */
    private static ResponseStatusException refused() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "That number and code do not go together, so nothing is confirmed.");
    }

    /**
     * The public answer about one request. Its own type, because the fields the
     * form prints and the fields a reader outside the platform may see are not
     * the same list: no staff record, no reason in the applicant's words and no
     * name of anyone who decided it travels here.
     */
    public record LeaveVerification(String reference, String applicantName, String designation,
            String department, String leaveType, LocalDate startDate, LocalDate endDate, int days,
            String statusLabel, boolean compensatory) {
    }

    /** A print run is a pile, not a year; the screen offers dozens either way. */
    private static List<Long> chosen(List<Long> ids, String noun) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose at least one " + noun + " to print.");
        }
        if (ids.size() > PRINT_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "One print run carries at most " + PRINT_LIMIT + " " + noun + "s."
                            + " Print another pile.");
        }
        return ids;
    }

    /**
     * The request as the paper states it. The supervisor's and the decision
     * maker's names are read from the staff records they point at, so a form
     * printed today shows who holds those records now rather than a name that
     * was copied when the request was made.
     */
    private IsoForm toForm(LeaveApplication application) {
        Employee employee = application.getEmployee();
        String reference = referenceOf(application);
        int year = application.getStartDate().getYear();
        boolean head = headsTheDepartment(employee);
        return new IsoForm(reference,
                verification.isConfigured() ? verification.codeFor(reference) : null,
                verification.isConfigured() ? verification.payloadFor(reference) : null,
                employee.getDepartment().getName(), employee.getFullName(),
                head ? HEAD_DESIGNATION_ON_PAPER : employee.getDesignation(), employee.getEmployeeNo(),
                application.getStartDate(), application.getEndDate(), application.getDays(),
                application.getReason(), application.getCreatedAt().atZone(ZoneId.systemDefault())
                        .toLocalDate(),
                application.getStatus().label(), verdictOf(application.getStatus()), head,
                nameOf(application.getSupervisorEmployeeId()),
                application.getSupervisorDecidedAt() == null ? null
                        : application.getSupervisorDecidedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                nameOf(application.getDecidedByEmployeeId()),
                application.getDecidedAt() == null ? null
                        : application.getDecidedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                application.isCompensated(),
                applications.daysInYear(employee.getId(), LeaveStatus.APPROVED,
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), application.getId()));
    }

    /**
     * The number the paper quotes. A row that has just been saved has not read
     * its own trigger-written column back yet, so the display copy is stamped
     * here in the same shape - the database stays the only writer of the column.
     */
    private static String referenceOf(LeaveApplication application) {
        if (application.getReference() == null) {
            application.stampReference();
        }
        return application.getReference();
    }

    /**
     * One person's year as the sheet states it. The three balance figures come
     * from the same year's balance row the staff summary reads, and an employee
     * with no row yet is still shown the allowance they are entitled to rather
     * than nothing; the lines come from the same overlap filter the lists use,
     * so a request that starts in December and ends in January is on both
     * years' records instead of missing from one.
     */
    private EmployeeRecord toRecord(Employee employee, LeaveType casual, int year) {
        LeaveBalance balance = balances.findByEmployeeIdAndLeaveTypeIdAndYear(
                employee.getId(), casual.getId(), year).orElse(null);
        int allocated = balance == null ? casual.getAnnualAllowance() : balance.getAllocated();
        int used = balance == null ? 0 : balance.getUsed();

        Specification<LeaveApplication> query =
                LeaveApplicationSpecifications.employee(employee.getId())
                        .and(LeaveApplicationSpecifications.filter(new LeaveFilter(
                                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), null)));
        // Every line, not the screen's ceiling: a sheet that quietly stopped
        // short would leave days off the paper somebody signs.
        List<EmployeeRecord.Entry> entries = new ArrayList<>();
        for (int page = 0;; page++) {
            List<EmployeeRecord.Entry> chunk = applications.findAll(query,
                    PageRequest.of(page, EXPORT_PAGE, ENTRY_ORDER)).stream()
                    .map(ReportService::toEntry)
                    .toList();
            entries.addAll(chunk);
            if (chunk.size() < EXPORT_PAGE) {
                return new EmployeeRecord(employee.getEmployeeNo(), employee.getFullName(),
                        employee.getDesignation(), employee.getDepartment().getName(), year,
                        allocated, used, entries);
            }
        }
    }

    private static EmployeeRecord.Entry toEntry(LeaveApplication application) {
        ZoneId zone = ZoneId.systemDefault();
        return new EmployeeRecord.Entry(referenceOf(application), application.getStartDate(),
                application.getEndDate(), application.getDays(), application.getStatus(),
                application.isCompensated(), application.getDecidedAt() == null ? null
                        : application.getDecidedAt().atZone(zone).toLocalDate());
    }

    /** The decision in one yes-or-no word, or null while nobody has decided. */
    private static Boolean verdictOf(LeaveStatus status) {
        return switch (status) {
            case APPROVED -> Boolean.TRUE;
            case REJECTED -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Whether this employee heads the department they belong to, which decides
     * whose signature the paper asks for: a department head's own leave is
     * sanctioned one level up, as it is in the workflow.
     */
    private boolean headsTheDepartment(Employee employee) {
        return users.findByEmployeeId(employee.getId())
                .filter(account -> hasRole(account, AppRole.HOD))
                .map(account -> employee.getDepartment().getId()
                        .equals(account.getEmployee().getDepartment().getId()))
                .orElse(false);
    }

    private String nameOf(Long employeeId) {
        return employeeId == null ? null
                : employees.findById(employeeId).map(Employee::getFullName).orElse(null);
    }

    /**
     * The sheet is built from what the platform actually holds: the leave
     * requests covering each day, the holiday calendar and the year's balance. A
     * day that is a weekend or a holiday is marked as that even when a request
     * spans it, because the day counter never charged those days either.
     */
    private AttendanceSheet buildSheet(Long scope, int year, int month) {
        checkYear(year);
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a month between 1 and 12.");
        }
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.plusMonths(1).minusDays(1);
        LeaveType casual = casualType();
        Set<LocalDate> holidays = new HashSet<>(calendar.findDatesBetween(first, last));
        List<AttendanceRow> rows = new ArrayList<>();

        // One read for the month's requests, whatever department they fall in,
        // and one for the balances: a sheet is signed as a whole, so it cannot
        // be built by asking after each employee separately.
        Map<Long, List<LeaveApplication>> byEmployee = new HashMap<>();
        for (LeaveApplication application : applications.findAll(
                registerQuery(scope, new LeaveFilter(first, last, null)))) {
            byEmployee.computeIfAbsent(application.getEmployee().getId(), key -> new ArrayList<>())
                    .add(application);
        }
        Map<Long, Integer> allocatedByEmployee = new HashMap<>();
        Map<Long, Integer> usedByEmployee = new HashMap<>();
        for (LeaveBalanceRepository.YearBalance balance
                : balances.findAllForYear(casual.getId(), year, scope)) {
            allocatedByEmployee.put(balance.getEmployeeId(), balance.getAllocated());
            usedByEmployee.put(balance.getEmployeeId(), balance.getUsed());
        }

        int days = last.getDayOfMonth();
        for (Employee employee : activeStaff(scope)) {
            List<String> marks = new ArrayList<>(days);
            int approved = 0;
            int waiting = 0;
            for (int day = 1; day <= days; day++) {
                String mark = markFor(first.plusDays(day - 1), holidays,
                        byEmployee.getOrDefault(employee.getId(), List.of()));
                marks.add(mark);
                if (mark.equals(AttendanceSheet.WAITING)) {
                    waiting++;
                } else if (!mark.isEmpty() && !mark.equals(AttendanceSheet.WEEKEND)
                        && !mark.equals(AttendanceSheet.HOLIDAY)) {
                    approved++;
                }
            }
            int allocated = allocatedByEmployee.getOrDefault(employee.getId(), casual.getAnnualAllowance());
            int used = usedByEmployee.getOrDefault(employee.getId(), 0);
            rows.add(new AttendanceRow(employee.getEmployeeNo(), employee.getFullName(),
                    employee.getDepartment().getName(), employee.getDesignation(), List.copyOf(marks),
                    approved, waiting, allocated, used, allocated - used));
        }
        return new AttendanceSheet(year, month, first, last, List.copyOf(rows));
    }

    /**
     * What one day says about one employee. The nature of the day comes first,
     * then an approved request, then one still waiting, and a plain blank when
     * nothing is recorded - never a claim that the person was at work.
     */
    private static String markFor(LocalDate day, Set<LocalDate> holidays,
            List<LeaveApplication> covering) {
        DayOfWeek weekday = day.getDayOfWeek();
        if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
            return AttendanceSheet.WEEKEND;
        }
        if (holidays.contains(day)) {
            return AttendanceSheet.HOLIDAY;
        }
        String approved = null;
        boolean waiting = false;
        for (LeaveApplication application : covering) {
            if (day.isBefore(application.getStartDate()) || day.isAfter(application.getEndDate())) {
                continue;
            }
            if (application.getStatus() == LeaveStatus.APPROVED) {
                approved = application.getLeaveType().getCode() + (application.isCompensated() ? "*" : "");
            } else if (application.getStatus().isOpen()) {
                waiting = true;
            }
        }
        return approved != null ? approved : waiting ? AttendanceSheet.WAITING : "";
    }

    /** The day numbers of the month, between the name of the employee and the totals. */
    private static List<String> sheetHeadings(AttendanceSheet sheet) {
        List<String> headings = new ArrayList<>(List.of("Staff No", "Name", "Department", "Designation"));
        for (int day = 1; day <= sheet.days(); day++) {
            headings.add(String.valueOf(day));
        }
        headings.addAll(List.of("Approved Days", "Waiting Days", "Granted", "Used", "Left"));
        return headings;
    }

    private static List<String> sheetCells(AttendanceRow row) {
        List<String> cells = new ArrayList<>(List.of(empty(row.employeeNo()), row.employeeName(),
                row.department(), row.designation()));
        cells.addAll(row.marks());
        cells.addAll(List.of(String.valueOf(row.approvedDays()), String.valueOf(row.waitingDays()),
                String.valueOf(row.allocated()), String.valueOf(row.used()),
                String.valueOf(row.remaining())));
        return cells;
    }

    /** The year a summary may be built for; anything else is a mistyped box. */
    private static void checkYear(int year) {
        if (year < 1900 || year > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a year between 1900 and 9999.");
        }
    }

    /**
     * The same rows the screen shows, a page at a time with no ceiling. The
     * department and period stay in the query for every page, so one file cannot
     * mix two different answers.
     */
    private List<LeaveRegisterRow> allRegisterRows(Long scope, LeaveFilter filter) {
        Specification<LeaveApplication> query = registerQuery(scope, filter);
        List<LeaveRegisterRow> rows = new ArrayList<>();
        for (int page = 0;; page++) {
            List<LeaveRegisterRow> chunk = applications.findAll(query,
                    PageRequest.of(page, EXPORT_PAGE, REGISTER_ORDER)).stream()
                    .map(ReportService::toRegisterRow)
                    .toList();
            rows.addAll(chunk);
            if (chunk.size() < EXPORT_PAGE) {
                return rows;
            }
        }
    }

    private List<StaffSummaryRow> buildSummary(Long scope, int year) {
        checkYear(year);
        LeaveType casual = casualType();
        List<Employee> staff = activeStaff(scope);

        Map<Long, Integer> allocatedByEmployee = new HashMap<>();
        Map<Long, Integer> usedByEmployee = new HashMap<>();
        for (LeaveBalanceRepository.YearBalance balance
                : balances.findAllForYear(casual.getId(), year, scope)) {
            allocatedByEmployee.put(balance.getEmployeeId(), balance.getAllocated());
            usedByEmployee.put(balance.getEmployeeId(), balance.getUsed());
        }

        // The day counts arrive per employee and status, grouped by the
        // database. Only the figures a person reads are kept. An employee who
        // has left also has totals here and is ignored: they are not on a sheet
        // somebody signs.
        Map<Long, Integer> approvedByEmployee = new HashMap<>();
        Map<Long, Integer> waitingByEmployee = new HashMap<>();
        Map<Long, Integer> requestsByEmployee = new HashMap<>();
        for (LeaveApplicationRepository.EmployeeStatusTotals total : applications.totalsForYear(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), scope)) {
            requestsByEmployee.merge(total.getEmployeeId(), total.getRequests().intValue(), Integer::sum);
            if (total.getStatus() == LeaveStatus.APPROVED) {
                approvedByEmployee.put(total.getEmployeeId(), total.getDays().intValue());
            } else if (total.getStatus().isOpen()) {
                waitingByEmployee.merge(total.getEmployeeId(), total.getDays().intValue(), Integer::sum);
            }
        }

        List<StaffSummaryRow> rows = new ArrayList<>();
        for (Employee employee : staff) {
            // An employee whose first request has not been approved yet has no
            // balance row; the allowance they are entitled to is still theirs,
            // so the sheet shows it rather than leaving them out.
            int allocated = allocatedByEmployee.getOrDefault(employee.getId(), casual.getAnnualAllowance());
            int used = usedByEmployee.getOrDefault(employee.getId(), 0);
            rows.add(new StaffSummaryRow(employee.getId(), employee.getEmployeeNo(), employee.getFullName(),
                    employee.getDepartment().getName(), employee.getDesignation(), year, allocated, used,
                    allocated - used, approvedByEmployee.getOrDefault(employee.getId(), 0),
                    waitingByEmployee.getOrDefault(employee.getId(), 0),
                    requestsByEmployee.getOrDefault(employee.getId(), 0)));
        }
        return rows;
    }

    /**
     * Whose names belong on a sheet: everyone still serving, in the department
     * the caller may see, in the order a reader follows. An employee who has
     * left is off it - a signed sheet is a live record, not an archive.
     */
    private List<Employee> activeStaff(Long scope) {
        return scope == null ? employees.findByActiveTrueOrderByFullNameAsc()
                : employees.findByDepartmentIdAndActiveTrueOrderByFullNameAsc(scope);
    }

    private static Specification<LeaveApplication> registerQuery(Long scope, LeaveFilter filter) {
        Specification<LeaveApplication> query = LeaveApplicationSpecifications.filter(filter);
        return scope == null ? query : query.and(LeaveApplicationSpecifications.department(scope));
    }

    /**
     * Which department a report may cover, settled before anything is read. A
     * central administrator may name one department or none, and none means the
     * whole university. An HOD is fixed to their own: asking for another is
     * refused rather than quietly answered with their own data.
     */
    private Long scopeOf(String username, Long requested) {
        AppUser user = account(username);
        if (hasRole(user, AppRole.CENTRAL_ADMIN)) {
            return requested;
        }
        if (hasRole(user, AppRole.HOD)) {
            Long own = user.getEmployee().getDepartment().getId();
            if (requested != null && !requested.equals(own)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A department head can only report on their own department.");
            }
            return own;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only a department head or a central administrator can read these reports.");
    }

    private void exported(String report, Long scope, String what) {
        String where = scope == null ? "the whole university"
                : "department " + departments.findById(scope)
                        .map(department -> department.getName()).orElse(String.valueOf(scope));
        audit.record(AuditAction.REPORT_EXPORTED, "REPORT", report.replace(' ', '-'),
                "Downloaded the " + report + " as a spreadsheet file for " + where + "; " + what + ".");
    }

    private AppUser account(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private LeaveType casualType() {
        return types.findByCodeAndActiveTrue(LeaveType.CASUAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "The casual leave type is not configured."));
    }

    private static boolean hasRole(AppUser user, String roleCode) {
        return user.getRoles().stream().anyMatch(role -> roleCode.equals(role.getCode()));
    }

    private static LeaveRegisterRow toRegisterRow(LeaveApplication application) {
        Employee employee = application.getEmployee();
        ZoneId zone = ZoneId.systemDefault();
        return new LeaveRegisterRow(application.getId(), referenceOf(application),
                employee.getEmployeeNo(), employee.getFullName(),
                employee.getDepartment().getName(), application.getLeaveType().getCode(),
                application.getStartDate(), application.getEndDate(), application.getDays(),
                application.getStatus(), application.getStatus().label(), application.isCompensated(),
                application.getReason(), application.getRemarks(),
                application.getCreatedAt().atZone(zone).toLocalDate(),
                application.getDecidedAt() == null ? null
                        : application.getDecidedAt().atZone(zone).toLocalDate());
    }

    private static List<String> registerCells(LeaveRegisterRow row) {
        return List.of(String.valueOf(row.id()), empty(row.reference()),
                empty(row.employeeNo()), row.employeeName(),
                row.department(), row.leaveType(), String.valueOf(row.startDate()),
                String.valueOf(row.endDate()), String.valueOf(row.days()), row.statusLabel(),
                row.compensatory() ? "Yes" : "", empty(row.reason()), empty(row.remarks()),
                String.valueOf(row.appliedOn()), row.decidedOn() == null ? "" : String.valueOf(row.decidedOn()));
    }

    private static List<String> summaryCells(StaffSummaryRow row) {
        return List.of(empty(row.employeeNo()), row.employeeName(), row.department(), row.designation(),
                String.valueOf(row.year()), String.valueOf(row.allocated()), String.valueOf(row.used()),
                String.valueOf(row.remaining()), String.valueOf(row.approvedDays()),
                String.valueOf(row.waitingDays()), String.valueOf(row.requests()));
    }

    /** A staff number is optional while staff data arrives in stages. */
    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
