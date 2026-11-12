package com.example.leaveplatform.leave;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Counts leave days the way the application it replaces does: both dates are
 * included, weekends count, and a day is excluded only when it is a public
 * holiday that falls on Monday to Friday. See docs/LEAVE-RULES.md.
 */
public final class LeaveDayCounter {

    private LeaveDayCounter() {
    }

    public static int countInclusive(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        int days = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY
                    || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend || !holidays.contains(day)) {
                days++;
            }
        }
        return days;
    }
}
