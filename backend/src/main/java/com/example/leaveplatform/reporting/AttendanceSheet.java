package com.example.leaveplatform.reporting;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * One month of the attendance sheet: which month it is and the line for every
 * employee the caller may see.
 *
 * <p>The letters in the cells mean the same thing on the screen, in the file and
 * in the legend, so they are stated once here rather than in three places.
 */
public record AttendanceSheet(
        int year,
        int month,
        LocalDate firstDay,
        LocalDate lastDay,
        List<AttendanceRow> rows) {

    /** Saturday or Sunday - the day nobody is expected to be at work. */
    public static final String WEEKEND = "W";

    /** A university holiday in the holiday calendar. */
    public static final String HOLIDAY = "H";

    /** A request covering the day that nobody has decided yet. */
    public static final String WAITING = "?";

    public int days() {
        return lastDay.getDayOfMonth();
    }

    /** November 2026 - what the screen and the file name call the sheet. */
    public String label() {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    /** The one line that teaches the letters, so a sheet is not read by guesswork. */
    public String legend() {
        return "W: weekend. H: university holiday. CL: approved casual leave, CL*: approved as "
                + "compensatory. ?: a request covering that day is still waiting for a decision. "
                + "A blank day has no approved leave recorded for it - the platform does not "
                + "record attendance, so a blank is not a mark of presence.";
    }
}
