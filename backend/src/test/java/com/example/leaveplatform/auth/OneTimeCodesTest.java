package com.example.leaveplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The maths, checked against the numbers the standard publishes.
 *
 * <p>RFC 6238 Appendix B prints what a given seed and a given time must give
 * out, so these tests are not a description of what this code happens to do.
 * They are the reason an authenticator app from any maker, on any phone, agrees
 * with this platform: if a test here failed, every app in the university would
 * disagree with it and nobody could sign in.
 *
 * <p>The published table is eight digits long; the platform asks for six. Both
 * are checked, because six is the same truncation with a smaller modulus, and
 * reading the published value modulo one million is how the shipped number is
 * tied to the standard rather than to itself.
 */
class OneTimeCodesTest {

    /**
     * RFC 6238's own test seed for SHA-1: the characters 1234567890 twice,
     * which is twenty characters and twenty bytes, and is how the standard's
     * 160-bit seed is written down.
     */
    private static final String RFC_SEED = "12345678901234567890";

    /** The same seed as Base32, which is what an app is given and what this class takes. */
    private static final String RFC_SEED_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static final long THIRTY = 30;

    @Test
    void encodesTheStandardSeedToTheStandardText() {
        assertThat(OneTimeCodes.encode(RFC_SEED.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo(RFC_SEED_BASE32);
    }

    @Test
    void readsTheStandardTextBackToTheStandardSeed() {
        assertThat(new String(OneTimeCodes.decode(RFC_SEED_BASE32),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(RFC_SEED);
    }

    @ParameterizedTest
    @MethodSource("rfc6238Table")
    void givesTheCodeTheStandardPublishes(long epochSeconds, String publishedEightDigits) {
        String eight = OneTimeCodes.at(RFC_SEED_BASE32, Instant.ofEpochSecond(epochSeconds), THIRTY, 8);

        assertThat(eight).isEqualTo(publishedEightDigits);
    }

    @ParameterizedTest
    @MethodSource("rfc6238Table")
    void givesSixDigitsFromTheSamePublishedValue(long epochSeconds, String publishedEightDigits) {
        String six = OneTimeCodes.at(RFC_SEED_BASE32, Instant.ofEpochSecond(epochSeconds), THIRTY, 6);

        assertThat(six).isEqualTo(publishedEightDigits.substring(publishedEightDigits.length() - 6));
        assertThat(six).hasSize(6);
    }

    static Stream<Arguments> rfc6238Table() {
        return Stream.of(
                Arguments.of(59L, "94287082"),
                Arguments.of(1111111109L, "07081804"),
                Arguments.of(1111111111L, "14050471"),
                Arguments.of(1234567890L, "89005924"),
                Arguments.of(2000000000L, "69279037"),
                Arguments.of(20000000000L, "65353130"));
    }

    @Test
    void keepsLeadingZerosThatAShorterReadingWouldDrop() {
        // 07081804 is in the published table because a code is a fixed number of
        // characters, not a number: an app showing "081804" and a platform that
        // had read 81804 are the same code only while both keep the zero.
        assertThat(OneTimeCodes.at(RFC_SEED_BASE32, Instant.ofEpochSecond(1111111109L), THIRTY, 6))
                .isEqualTo("081804")
                .hasSize(6);
    }

    @Test
    void acceptsTheCodeItJustGaveOut() {
        Instant now = Instant.ofEpochSecond(1700000000L);
        String code = OneTimeCodes.at(RFC_SEED_BASE32, now, THIRTY, 6);

        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, code, now, THIRTY, 6, 1)).isTrue();
    }

    @Test
    void forgivesOneStepOfClockSkewEitherWay() {
        Instant now = Instant.ofEpochSecond(1700000001L);
        String lastStep = OneTimeCodes.at(RFC_SEED_BASE32, now.minusSeconds(THIRTY), THIRTY, 6);
        String nextStep = OneTimeCodes.at(RFC_SEED_BASE32, now.plusSeconds(THIRTY), THIRTY, 6);

        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, lastStep, now, THIRTY, 6, 1)).isTrue();
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, nextStep, now, THIRTY, 6, 1)).isTrue();
    }

    @Test
    void refusesACodeTwoStepsOldEvenThoughItWasReal() {
        Instant now = Instant.ofEpochSecond(1700000001L);
        String twoStepsAgo = OneTimeCodes.at(RFC_SEED_BASE32, now.minusSeconds(2 * THIRTY), THIRTY, 6);

        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, twoStepsAgo, now, THIRTY, 6, 1)).isFalse();
        // ...and the window is not a knob to turn upwards without saying so.
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, twoStepsAgo, now, THIRTY, 6, 2)).isTrue();
    }

    @Test
    void refusesAnythingThatIsNotSixDigits() {
        Instant now = Instant.ofEpochSecond(1700000000L);
        String code = OneTimeCodes.at(RFC_SEED_BASE32, now, THIRTY, 6);

        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, null, now, THIRTY, 6, 1)).isFalse();
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, "", now, THIRTY, 6, 1)).isFalse();
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, "12345", now, THIRTY, 6, 1)).isFalse();
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, "1234567", now, THIRTY, 6, 1)).isFalse();
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, "abcdef", now, THIRTY, 6, 1)).isFalse();
        // Spaces and the surrounding air of a pasted number are forgiven; a code
        // that is the wrong length after they go is still the wrong code.
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, code.charAt(0) + " " + code.substring(1),
                now, THIRTY, 6, 1)).isTrue();
        assertThat(OneTimeCodes.matches(RFC_SEED_BASE32, " " + code + " ", now, THIRTY, 6, 1)).isTrue();
    }

    @Test
    void readsTheSeedHoweverAPersonCopiesIt() {
        Instant now = Instant.ofEpochSecond(1700000000L);
        String code = OneTimeCodes.at(RFC_SEED_BASE32, now, THIRTY, 6);
        String messy = " gezdgnbvg" + "y3tqo" + "jq" + "-" + "gezdg" + "nbvgy3tqo" + "jq  ";

        assertThat(OneTimeCodes.matches(messy, code, now, THIRTY, 6, 1)).isTrue();
    }

    @Test
    void refusesASeedWithACharacterOutsideItsAlphabet() {
        // The letter 1 is not in this alphabet; a seed typed with an O for a zero
        // has to be said out loud, not read as a different seed that gives
        // different codes forever.
        assertThatThrownBy(() -> OneTimeCodes.decode("GEZDGNBVGY3TQOJQ!EZDGNBVGY3TQOJQ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not one of its characters");
    }

    @Test
    void refusesASeedTooShortToBeWorthAnything() {
        assertThatThrownBy(() -> OneTimeCodes.decode("GEZDGNBV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void makesSeedsThatAreDifferentAndTheRightLength() {
        String first = OneTimeCodes.newSeed();
        String second = OneTimeCodes.newSeed();

        assertThat(first).hasSize(32).isNotEqualTo(second);
        assertThat(OneTimeCodes.decode(first)).hasSize(20);
    }

    @Test
    void groupsASeedForSomebodyTypingItByHand() {
        assertThat(OneTimeCodes.grouped("ABCDEFGHIJKLMNOP"))
                .isEqualTo("ABCD EFGH IJKL MNOP");
        // Four characters is the point: past that a person loses their place.
        assertThat(OneTimeCodes.grouped("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
                .startsWith("GEZD GNBV")
                .doesNotContain("     ");
    }

    @Test
    void aGroupedSeedStillDecodesToTheSameSeed() {
        String seed = OneTimeCodes.newSeed();

        assertThat(OneTimeCodes.decode(OneTimeCodes.grouped(seed)))
                .isEqualTo(OneTimeCodes.decode(seed));
    }
}
