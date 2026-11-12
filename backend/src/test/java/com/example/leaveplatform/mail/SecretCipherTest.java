package com.example.leaveplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The rules about the saved mail password: it round-trips, it is never the same
 * text twice, and a value the platform cannot trust is refused instead of tried
 * against the university's mail server.
 */
class SecretCipherTest {

    private static final String KEY = "a phrase only the server knows";

    @Test
    void aSavedPasswordReadsBackAsWhatWasTyped() {
        String sealed = SecretCipher.encrypt("SMTP password", KEY);

        assertThat(sealed).startsWith("v1:").doesNotContain("SMTP password");
        assertThat(SecretCipher.decrypt(sealed, KEY)).isEqualTo("SMTP password");
    }

    @Test
    void theSamePasswordTwiceIsNotStoredTwiceTheSameWay() {
        assertThat(SecretCipher.encrypt("again", KEY)).isNotEqualTo(SecretCipher.encrypt("again", KEY));
    }

    @Test
    void aPasswordSavedUnderAnotherKeyIsRefusedRatherThanGuessedAt() {
        String sealed = SecretCipher.encrypt("correct horse", KEY);

        assertThatThrownBy(() -> SecretCipher.decrypt(sealed, "a different phrase"))
                .isInstanceOf(MailSettingException.class)
                .hasMessageContaining("could not be decrypted");
    }

    @Test
    void aHandEditedValueIsRefusedAndNeverTriedAgainstTheServer() {
        assertThatThrownBy(() -> SecretCipher.decrypt("v1:c2VjcmV0", KEY))
                .isInstanceOf(MailSettingException.class);
        assertThat(SecretCipher.isWrapped("plainly written password")).isFalse();
    }

    @Test
    void noPasswordStoredMeansNoAttemptAndNoError() {
        assertThat(SecretCipher.decrypt(null, KEY)).isNull();
        assertThat(SecretCipher.decrypt("  ", KEY)).isNull();
    }
}
