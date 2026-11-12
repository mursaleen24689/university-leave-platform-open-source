package com.example.leaveplatform.identity;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Password rules for local accounts.
 *
 * <p>The common-password list is deliberately small and embedded so that
 * validation never depends on a network call. A full breached-password list
 * (for example the Pwned Passwords range API) can replace it later without
 * changing callers.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    /** BCrypt ignores anything beyond 72 bytes, so longer values are refused. */
    static final int MAX_LENGTH = 72;
    private static final int MIN_USERNAME_LENGTH_IN_PASSWORD = 3;

    private static final Set<String> COMMON = Set.of(
            "123456789012", "1234567890123", "12345678901234", "password1234",
            "passwordpassword", "qwertyuiop123", "administrator", "letmein12345",
            "welcome12345", "changeme1234", "iloveyou1234", "trustno1trustno1",
            "qwerty123456", "1qaz2wsx3edc", "abc123456789", "p@ssw0rdp@ssw0rd",
            "password!234", "adminadminadmin", "university123", "neduniversity");

    public void check(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new WeakPasswordException(
                    "Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new WeakPasswordException(
                    "Password must be at most " + MAX_LENGTH + " characters.");
        }
        String lower = rawPassword.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw new WeakPasswordException("That password is too common. Choose another one.");
        }
        if (username != null && username.length() >= MIN_USERNAME_LENGTH_IN_PASSWORD
                && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw new WeakPasswordException("Password must not contain the username.");
        }
    }
}
