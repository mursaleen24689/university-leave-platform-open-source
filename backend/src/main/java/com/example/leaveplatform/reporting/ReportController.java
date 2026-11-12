package com.example.leaveplatform.reporting;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveStatus;
import com.example.leaveplatform.limit.RequestThrottle;

/**
 * JSON and spreadsheet reads of the reports. {@code SecurityConfig} allows a
 * central administrator and an HOD only; which rows come back is the service's
 * decision, so a caller who reaches this class with someone else's department
 * number still gets a refusal.
 *
 * <p>The screen at {@code /reports} uses the same service, not these endpoints.
 * They exist so a script or another system can ask the same question, and so
 * the rules can be checked without a browser.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final RequestThrottle verifyLimit;

    public ReportController(ReportService reports, RequestThrottle verifyLimit) {
        this.reports = reports;
        this.verifyLimit = verifyLimit;
    }

    @GetMapping("/register")
    List<LeaveRegisterRow> register(Authentication authentication,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) LeaveStatus status) {
        return reports.register(authentication.getName(), departmentId, new LeaveFilter(from, to, status));
    }

    @GetMapping("/register/csv")
    ResponseEntity<byte[]> registerCsv(Authentication authentication,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) LeaveStatus status) {
        return download(reports.registerCsv(authentication.getName(), departmentId,
                new LeaveFilter(from, to, status)));
    }

    @GetMapping("/staff-summary")
    List<StaffSummaryRow> staffSummary(Authentication authentication,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer year) {
        return reports.staffSummary(authentication.getName(), departmentId, yearOf(year));
    }

    @GetMapping("/staff-summary/csv")
    ResponseEntity<byte[]> staffSummaryCsv(Authentication authentication,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer year) {
        return download(reports.staffSummaryCsv(authentication.getName(), departmentId, yearOf(year)));
    }

    @GetMapping("/attendance")
    AttendanceSheet attendance(Authentication authentication,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return reports.attendanceSheet(authentication.getName(), departmentId, yearOf(year), monthOf(month));
    }

    @GetMapping("/attendance/csv")
    ResponseEntity<byte[]> attendanceCsv(Authentication authentication,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return download(reports.attendanceCsv(authentication.getName(), departmentId,
                yearOf(year), monthOf(month)));
    }

    /**
     * The application forms for the chosen requests, as a page the browser
     * prints. HTML rather than a PDF because the office prints from the same
     * screen it works from, and a printer driver measures a millimetre box
     * better than any library in this application would.
     */
    @GetMapping(value = "/iso-form", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> isoForm(Authentication authentication, @RequestParam List<Long> ids) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(reports.isoForms(authentication.getName(), ids));
    }

    /**
     * One officer's leave record for one year, as a page the browser prints -
     * a sheet per name, in the order the office ticked them.
     */
    @GetMapping(value = "/employee-record", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> employeeRecord(Authentication authentication,
            @RequestParam List<Long> ids, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(reports.employeeRecords(authentication.getName(), ids, yearOf(year)));
    }

    /**
     * The read a printed form is checked against, open to anybody who holds the
     * paper. The number alone answers nothing: the code beside it is what shows
     * the form came out of this platform, and a pair that does not go together
     * is refused without saying which half was wrong.
     *
     * <p>{@code SecurityConfig} allows this one path under {@code /reports/}
     * without a signed-in account; everything else there still refuses.
     *
     * <p>Being open, it is also counted: one address may ask only so many times a
     * minute, and past that it is turned away for the rest of the window without
     * the database being read at all. The limit is not what keeps a forgery out -
     * a guessed code is one chance in 6.9e14 whatever the rate - it is what keeps
     * a public door from becoming a way to load this server.
     */
    @GetMapping("/verify")
    ResponseEntity<?> verify(@RequestParam(required = false) String reference,
            @RequestParam(required = false) String code, HttpServletRequest request) {
        Optional<Duration> waitLeft = verifyLimit.refusalFor(request.getRemoteAddr());
        if (waitLeft.isPresent()) {
            long seconds = Math.max(1, waitLeft.get().toSeconds());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "This address has checked too many forms just now. Try again in " + seconds + " seconds.");
            problem.setTitle("Form check refused");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                    .body(problem);
        }
        return ResponseEntity.ok(reports.verify(reference, code));
    }

    /** No year named means the running one, which is the sheet people want. */
    private static int yearOf(Integer year) {
        return year == null ? LocalDate.now().getYear() : year;
    }

    /** No month named means the running one, for the same reason. */
    private static int monthOf(Integer month) {
        return month == null ? LocalDate.now().getMonthValue() : month;
    }

    private static ResponseEntity<byte[]> download(CsvFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }
}
