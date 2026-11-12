package com.example.leaveplatform.leave;

import java.time.LocalDate;

/**
 * How a leave list may be narrowed: a period and one status. Every part is
 * optional, and a blank part means "anything".
 *
 * <p>A request belongs to the period when it <em>overlaps</em> it, so leave from
 * 30 June to 3 July still appears in a July search. Matching only the first day
 * would hide days a department is actually missing without anyone on it knowing.
 */
public record LeaveFilter(LocalDate from, LocalDate to, LeaveStatus status) {

    public static LeaveFilter any() {
        return new LeaveFilter(null, null, null);
    }

    public LeaveFilter {
        if (from != null && to != null && to.isBefore(from)) {
            throw new LeaveRuleException("The last day of the period cannot be before the first day.");
        }
    }

    public boolean isAny() {
        return from == null && to == null && status == null;
    }

    /** What the screen says above a list that has been narrowed. */
    public String describe() {
        if (isAny()) {
            return "everything";
        }
        StringBuilder words = new StringBuilder();
        if (from != null && to != null) {
            words.append("from ").append(from).append(" to ").append(to);
        } else if (from != null) {
            words.append("from ").append(from).append(" onwards");
        } else if (to != null) {
            words.append("up to and including ").append(to);
        }
        if (status != null) {
            words.append(words.isEmpty() ? "" : ", ").append(status.label().toLowerCase());
        }
        return words.toString();
    }
}
