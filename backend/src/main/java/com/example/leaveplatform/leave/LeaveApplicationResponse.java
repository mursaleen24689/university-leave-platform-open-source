package com.example.leaveplatform.leave;

import java.time.Instant;
import java.time.LocalDate;

public record LeaveApplicationResponse(
        Long id,
        /** The number the printed application form quotes, for example LV-000123. */
        String reference,
        String employeeNo,
        String employeeName,
        String departmentName,
        String leaveTypeCode,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String reason,
        LeaveStatus status,
        String statusLabel,
        boolean compensated,
        String remarks,
        Instant submittedAt) {
}
