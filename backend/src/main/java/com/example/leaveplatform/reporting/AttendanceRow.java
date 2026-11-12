package com.example.leaveplatform.reporting;

import java.util.List;

/**
 * One employee's line on the monthly attendance sheet: the mark for every day of
 * the month, in day order, and the totals read from those same marks.
 *
 * <p>A blank cell means "no approved leave is recorded for this day". It does not
 * mean present: the platform records leave, not attendance, and a sheet that
 * wrote a P in every other box would be signing off on something nobody entered.
 */
public record AttendanceRow(
        String employeeNo,
        String employeeName,
        String department,
        String designation,
        List<String> marks,
        int approvedDays,
        int waitingDays,
        int allocated,
        int used,
        int remaining) {
}
