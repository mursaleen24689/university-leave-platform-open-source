package com.example.leaveplatform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * What a printed leave form may claim about itself.
 *
 * <p>The code is the difference between a form and a forgery, so these tests
 * keep two promises: the same server always says the same thing about the same
 * number, and nothing that does not hold the key can say it as well. The second
 * is proved the only way it can be - by showing that a second key gives a
 * different answer to the same request.
 */
class VerificationCodeTest {

    private static final String REFERENCE = "LV-000007";
    private static final String OTHER = "LV-000008";

    private final VerificationCode signed =
            new VerificationCode(new VerificationKey("the test material"), "");

    @Test
    void theSameNumberAlwaysGetsTheSameCode() {
        String code = signed.codeFor(REFERENCE);

        assertThat(code).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(signed.codeFor(REFERENCE)).isEqualTo(code);
    }

    @Test
    void aDifferentNumberGetsADifferentCode() {
        assertThat(signed.codeFor(OTHER)).isNotEqualTo(signed.codeFor(REFERENCE));
    }

    @Test
    void aSecondKeyCouldNotHaveWrittenTheFirstOnesCode() {
        // The whole point of the key: nothing printed on the form lets anybody
        // else work the code out, so a copied number with a made-up code fails.
        VerificationCode rival = new VerificationCode(new VerificationKey("other material"), "");

        assertThat(rival.codeFor(REFERENCE)).isNotEqualTo(signed.codeFor(REFERENCE));
        assertThat(signed.matches(REFERENCE, rival.codeFor(REFERENCE))).isFalse();
    }

    @Test
    void aCodeIsValidOnlyBesideTheNumberItWasMintedFor() {
        String code = signed.codeFor(REFERENCE);

        assertThat(signed.matches(REFERENCE, code)).isTrue();
        assertThat(signed.matches(OTHER, code)).isFalse();
    }

    @Test
    void aCodeTypedFromPaperStillChecks() {
        String code = signed.codeFor(REFERENCE);

        // A person reading a form over the telephone, or a scanner that keeps the
        // capital letters of the number, is not expected to match the case.
        assertThat(signed.matches(REFERENCE, code.toUpperCase())).isTrue();
        assertThat(signed.matches("  " + REFERENCE + "  ", " " + code + " ")).isTrue();
        assertThat(signed.matches("lv-000007", code)).isTrue();
    }

    @Test
    void aPairThatIsNotShapedLikeOneIsRefusedWithoutComparingAnything() {
        String code = signed.codeFor(REFERENCE);

        assertThat(signed.matches("HD-000007", code)).isFalse();
        assertThat(signed.matches("LV-", code)).isFalse();
        assertThat(signed.matches("LV-000007 OR 1=1", code)).isFalse();
        assertThat(signed.matches(REFERENCE, "not hex at all")).isFalse();
        assertThat(signed.matches(REFERENCE, code.substring(1))).isFalse();
        assertThat(signed.matches(null, code)).isFalse();
        assertThat(signed.matches(REFERENCE, null)).isFalse();
    }

    @Test
    void aServerWithNoKeyIssuesNothingAndChecksNothing() {
        VerificationCode unsigned = new VerificationCode(new VerificationKey(""), "");

        assertThat(unsigned.isConfigured()).isFalse();
        assertThatThrownBy(() -> unsigned.codeFor(REFERENCE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        // Not "everything matches", which is how a default key quietly becomes
        // the shape of this bug: without a key there is no answer to give.
        assertThat(unsigned.matches(REFERENCE, signed.codeFor(REFERENCE))).isFalse();
    }

    @Test
    void thePictureHoldsWordsWhenThePlatformDoesNotKnowWhereItLives() {
        String payload = signed.payloadFor(REFERENCE);

        assertThat(payload).isEqualTo("LEAVE-VERIFY|" + REFERENCE + "|" + signed.codeFor(REFERENCE));
    }

    @Test
    void thePictureHoldsAnAddressOnceThePlatformKnowsWhereItLives() {
        VerificationCode hosted =
                new VerificationCode(new VerificationKey("the test material"), "https://leave.example.edu/");

        String payload = hosted.payloadFor(REFERENCE);

        assertThat(payload).isEqualTo("https://leave.example.edu/api/v1/reports/verify?reference="
                + REFERENCE + "&code=" + signed.codeFor(REFERENCE));
        // The address is the words with the same code under them: a phone opens
        // it, and a person at a desk can still read the two parts off the paper.
        assertThat(payload).contains(signed.codeFor(REFERENCE));
    }

    @Test
    void aLongKeyAndAnOrdinaryOneBehaveTheSame() {
        VerificationCode longKey = new VerificationCode(
                new VerificationKey("   the test material   "), "");

        assertThat(longKey.codeFor(REFERENCE)).isEqualTo(signed.codeFor(REFERENCE));
    }
}
