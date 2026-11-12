package com.example.leaveplatform.reporting;

import java.time.LocalDate;
import java.util.List;
import com.example.leaveplatform.leave.LeaveStatus;

/**
 * Everything one person's printed leave record states, as the platform knows
 * it.
 *
 * <p>A separate type from an employee for the same reason {@link IsoForm} is
 * separate from a leave request: the page has to be testable with a hand-built
 * record instead of a database, and what the office files is a document of the
 * university rather than a dump of columns.
 *
 * @param employeeNo the staff number, which may still be blank while records
 *            are completed
 * @param name the officer's own name
 * @param designation as the staff record gives it
 * @param department the department the record belongs to
 * @param year the calendar year this sheet covers
 * @param allocated days granted for the year, from that year's balance row or
 *            the type's allowance when no row exists yet
 * @param used days already spent, as that same balance row states them
 * @param entries every request whose period touches this year, oldest first,
 *            whatever was decided about it
 */
public record EmployeeRecord(String employeeNo, String name, String designation, String department,
        int year, int allocated, int used, List<Entry> entries) {

    /** What is left of the year, worked out the one way the platform works it out. */
    public int remaining() {
        return allocated - used;
    }

    /**
     * A request as one line of the record. The status travels with it rather
     * than as a separate "approved" flag: an office that signs this sheet needs
     * to see a waiting request <em>as</em> a waiting request, and a line that
     * cannot be read as leave already taken is what makes the page safe to file.
     *
     * @param reference the number the register and the application form both
     *            quote, so a sheet and a screen are proved to be the same request
     * @param compensatory approved against duty on an off day, so no day came
     *            out of the year's balance
     * @param decidedOn the day somebody answered it, or null while nobody has
     */
    public record Entry(String reference, LocalDate startDate, LocalDate endDate, int days,
            LeaveStatus status, boolean compensatory, LocalDate decidedOn) {

        public boolean waiting() {
            return status.isOpen();
        }
    }
}
