package com.example.leaveplatform.organization;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Immutable department or office reference code. Codes are permanent once used
 * because imports, reports and audit records refer to them.
 */
public record DepartmentCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9-]{1,15}");

    public DepartmentCode {
        if (value == null) {
            throw new IllegalArgumentException("Department code is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Department code must be 2-16 letters, digits or hyphens");
        }
        value = normalized;
    }

    public static DepartmentCode of(String raw) {
        return new DepartmentCode(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
