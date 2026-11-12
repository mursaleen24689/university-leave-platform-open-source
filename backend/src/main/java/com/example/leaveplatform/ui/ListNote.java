package com.example.leaveplatform.ui;

import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveService;
import com.example.leaveplatform.notification.NotificationService;
import com.example.leaveplatform.reporting.AttendanceSheet;
import com.example.leaveplatform.reporting.ReportService;

/**
 * The one line a capped list needs above it: what the person filtered to, and
 * whether the server stopped counting. Without the second part, a person who
 * asks for a year and gets two hundred rows reads it as "this is all of it".
 */
final class ListNote {

    private ListNote() {
    }

    static String ofLeave(int size, LeaveFilter filter) {
        return of(size, filter, LeaveService.LIST_LIMIT, "Narrow the dates to see the rest");
    }

    /**
     * The register line. A file is offered beside the screen, so the sentence
     * says which of the two is complete: the list on the screen is the newest
     * rows, the download is everything the filter matched.
     */
    static String ofRegister(int size, LeaveFilter filter) {
        return of(size, filter, ReportService.SCREEN_LIMIT,
                "Narrow the dates, or download the file, which is not cut short");
    }

    static String ofSummary(int size, int year) {
        return employees(size, String.valueOf(year));
    }

    /**
     * The attendance line. Unlike the other two, this list is not cut short: a
     * sheet is signed as a whole, so everyone is on it and the count needs no
     * warning beside it.
     */
    static String ofSheet(int size, AttendanceSheet sheet) {
        return employees(size, sheet.label());
    }

    /**
     * The bell's line: how many messages are listed, how many of them are still
     * unread, and - when the server stopped counting - that the older ones are
     * still there rather than gone.
     */
    static String ofMessages(int size, long unread) {
        if (size == 0) {
            return "No messages.";
        }
        String line = "%d message%s listed, %d unread."
                .formatted(size, size == 1 ? "" : "s", unread);
        return size >= NotificationService.LIST_LIMIT
                ? line + " These are the newest " + NotificationService.LIST_LIMIT
                        + " - marking some read lets the older ones show."
                : line;
    }

    private static String employees(int size, String period) {
        return "%d employee%s for %s.".formatted(size, size == 1 ? "" : "s", period);
    }

    private static String of(int size, LeaveFilter filter, int ceiling, String advice) {
        StringBuilder words = new StringBuilder();
        words.append(size).append(size == 1 ? " request" : " requests");
        if (!filter.isAny()) {
            words.append(" matching ").append(filter.describe());
        }
        words.append('.');
        if (size >= ceiling) {
            words.append(" These are the newest ").append(ceiling).append(" - ").append(advice)
                    .append('.');
        }
        return words.toString();
    }
}
