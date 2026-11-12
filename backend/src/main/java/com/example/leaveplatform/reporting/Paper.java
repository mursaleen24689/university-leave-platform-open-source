package com.example.leaveplatform.reporting;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The three things every printed page of this platform needs: a date written
 * the way the university writes it, an absent value that prints as nothing
 * rather than as the word "null", and text that cannot become markup.
 *
 * <p>It lives in one place because the escaping rule is a security rule. Two
 * renderers with two copies of it are two chances for one of them to be fixed.
 */
final class Paper {

    static final DateTimeFormatter READABLE =
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    private Paper() {
    }

    static String day(LocalDate day) {
        return day == null ? "" : day.format(READABLE);
    }

    /** A staff number is optional while records are completed, so an empty is normal. */
    static String blank(String value) {
        return value == null ? "" : value;
    }

    /**
     * Everything written here comes out of a staff record, a reason someone
     * typed or a name someone chose, so nothing reaches the output unescaped.
     * The page is opened by a signed-in account, but a value containing markup
     * must not become markup.
     */
    static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
