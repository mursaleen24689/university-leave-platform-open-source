package com.example.leaveplatform.ui;

import java.util.Locale;

/**
 * The audit trail stores its action and target as one upper-case code with
 * underscores, because that is what a report can count and what the database
 * keeps. A code is not what a person reads, so the screen spells it out:
 * LEAVE_APPROVED becomes "Leave approved". Nothing is lost - the search box
 * still finds an entry by either spelling.
 */
final class ActionLabels {

    private ActionLabels() {
    }

    static String plain(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String words = String.join(" ", code.toLowerCase(Locale.ROOT).split("_"));
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
