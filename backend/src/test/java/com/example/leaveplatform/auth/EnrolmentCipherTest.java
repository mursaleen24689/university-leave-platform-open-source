package com.example.leaveplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The authenticator seed as it is kept: it round-trips, the same seed twice is
 * not stored twice the same way, and anything the platform cannot trust is
 * refused instead of quietly read as a different seed.
 */
class EnrolmentCipherTest {

    private static final String KEY = "a phrase only the server knows";
    private static final String SEED = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void aSavedSeedReadsBackAsWhatTheAppWasGiven() {
        String sealed = EnrolmentCipher.seal(SEED, KEY);

        assertThat(sealed).startsWith("v1:").doesNotContain(SEED);
        assertThat(EnrolmentCipher.open(sealed, KEY)).isEqualTo(SEED);
    }

    @Test
    void theSameSeedTwiceIsNotStoredTwiceTheSameWay() {
        assertThat(EnrolmentCipher.seal(SEED, KEY)).isNotEqualTo(EnrolmentCipher.seal(SEED, KEY));
    }

    @Test
    void aSeedSavedUnderAnotherKeyIsRefusedRatherThanGuessedAt() {
        String sealed = EnrolmentCipher.seal(SEED, KEY);

        // Not "the same length, so probably the same seed": an opened value that
        // is wrong would give codes that are wrong forever.
        assertThatThrownBy(() -> EnrolmentCipher.open(sealed, "a different phrase"))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("could not be opened");
    }

    @Test
    void aHandEditedOrTruncatedValueIsRefused() {
        String sealed = EnrolmentCipher.seal(SEED, KEY);

        assertThatThrownBy(() -> EnrolmentCipher.open("plainly written seed", KEY))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("not readable");
        assertThatThrownBy(() -> EnrolmentCipher.open(sealed.substring(0, sealed.length() - 4), KEY))
                .isInstanceOf(TwoStepRuleException.class);
        assertThatThrownBy(() -> EnrolmentCipher.open(null, KEY))
                .isInstanceOf(TwoStepRuleException.class);
    }

    @Test
    void aRealSeedSealedFitsTheColumnItIsSavedIn() {
        // V018 gives the column 200 characters. Whatever the app is handed has
        // to seal inside that, or the row would be saved cut short and open as
        // nothing at all - the one failure a person would see as a broken
        // sign-in with no idea why.
        String seed = OneTimeCodes.newSeed();
        String sealed = EnrolmentCipher.seal(seed, KEY);

        assertThat(sealed).hasSizeLessThanOrEqualTo(200);
        assertThat(EnrolmentCipher.open(sealed, KEY)).isEqualTo(seed);
    }
}
