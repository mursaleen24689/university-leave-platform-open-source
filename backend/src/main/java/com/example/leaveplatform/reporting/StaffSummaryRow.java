package com.example.leaveplatform.reporting;

/**
 * One person's leave position for one year. The allocated, used and remaining
 * days come from the balance row; the day counts beside them are read from the
 * requests themselves, so a sheet shows both what was granted and what is still
 * waiting.
 *
 * <p>The id is the employee's own, which the screen needs because a printed
 * record is asked for by the names ticked on this list. The spreadsheet file
 * never carried it and still does not: it is a number of this database, and a
 * person reading a file quotes a staff number.
 */
public record StaffSummaryRow(long id, String employeeNo, String employeeName, String department,
        String designation, int year, int allocated, int used, int remaining, int approvedDays,
        int waitingDays, int requests) {
}
