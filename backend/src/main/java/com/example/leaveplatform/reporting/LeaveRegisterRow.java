package com.example.leaveplatform.reporting;

import java.time.LocalDate;
import com.example.leaveplatform.leave.LeaveStatus;

/**
 * One line of the leave register: a request, who made it and what happened to
 * it. Every field is a value the request already carries - nothing here is
 * worked out from another source, so the register and the screen can never
 * disagree about the same request.
 *
 * <p>The reference is the number a printed application form quotes, which is
 * how an office matches the paper on the desk to the row in the register.
 */
public record LeaveRegisterRow(Long id, String reference, String employeeNo, String employeeName,
        String department, String leaveType, LocalDate startDate, LocalDate endDate, int days,
        LeaveStatus status, String statusLabel, boolean compensatory, String reason, String remarks,
        LocalDate appliedOn, LocalDate decidedOn) {
}
