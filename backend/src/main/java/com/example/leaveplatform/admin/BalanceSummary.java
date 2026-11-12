package com.example.leaveplatform.admin;

/** One employee's balance for one leave type and year, as the screen shows it. */
public record BalanceSummary(
        Long balanceId,
        String employeeNo,
        String employeeName,
        String leaveTypeCode,
        String leaveTypeName,
        int year,
        int allocated,
        int used,
        int remaining) {
}
