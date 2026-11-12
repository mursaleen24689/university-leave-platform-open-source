package com.example.leaveplatform.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> policy.check("Short1!pass", "z.ahmed"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void rejectsCommonPasswords() {
        for (String common : new String[] {"password1234", "administrator", "P@ssw0rdp@ssw0rd"}) {
            assertThatThrownBy(() -> policy.check(common, "z.ahmed"))
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessageContaining("too common");
        }
    }

    @Test
    void rejectsTheUsernameInsideThePassword() {
        assertThatThrownBy(() -> policy.check("z.ahmed-2026!", "z.ahmed"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("username");
    }

    @Test
    void ignoresUsernameContainmentForShortUsernames() {
        assertThatCode(() -> policy.check("quartz-harbour-2026", "pa"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsLongerThanBcryptAccepts() {
        String tooLong = "a".repeat(73);

        assertThatThrownBy(() -> policy.check(tooLong, "z.ahmed"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at most 72");
    }

    @Test
    void acceptsAReasonablePassphrase() {
        assertThatCode(() -> policy.check("quartz-harbour-2026", "z.ahmed"))
                .doesNotThrowAnyException();
    }
}
