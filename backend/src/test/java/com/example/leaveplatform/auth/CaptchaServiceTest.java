package com.example.leaveplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import com.example.leaveplatform.auth.CaptchaService.Challenge;
import com.example.leaveplatform.auth.CaptchaService.Picture;
import com.example.leaveplatform.limit.RequestThrottle;

class CaptchaServiceTest {

    /** The one way a reader of the picture may take a code out of it: the characters, in order. */
    private static final Pattern GLYPH = Pattern.compile("<text[^>]*>([^<]+)</text>");
    private static final String ADDRESS = "203.0.113.7";

    /** The shipped session counts, with an address limit no test in this class is about. */
    private final CaptchaService captcha = service(40, 3, 120, 100_000);

    @Test
    void thePictureShowsTheCodeTheServerThenExpects() {
        HttpSession session = new MockHttpSession();
        String picture = showPicture(session).svg();
        String code = readCode(picture);

        assertThat(picture).startsWith("<svg").doesNotContain("<script");
        for (int i = 0; i < code.length(); i++) {
            assertThat(picture).contains(">" + code.charAt(i) + "</text>");
        }
    }

    @Test
    void aCodeTypedInAnyCaseAndAnySpacingIsTheOneOnScreen() {
        HttpSession session = new MockHttpSession();
        String code = showCode(session);

        assertThat(captcha.check(session, "  " + code.toLowerCase() + " ")).isEqualTo(CaptchaService.PASSED);
    }

    @Test
    void onePictureBuysOneAttemptEvenWhenTheCodeWasRight() {
        HttpSession session = new MockHttpSession();
        String code = showCode(session);

        assertThat(captcha.check(session, code)).isEqualTo(CaptchaService.PASSED);
        assertThat(captcha.check(session, code)).isEqualTo(CaptchaService.CODE_MISSING);
    }

    @Test
    void aWrongCodeIsThrownAwayWithThePictureItBelongsTo() {
        HttpSession session = new MockHttpSession();
        String code = showCode(session);

        assertThat(captcha.check(session, "QQQQQQ")).isEqualTo(CaptchaService.CODE_WRONG);
        // The guesser cannot keep trying against one code, and the person who
        // typed honestly is shown a new picture for the next attempt.
        assertThat(captcha.check(session, code)).isEqualTo(CaptchaService.CODE_MISSING);
    }

    @Test
    void anEmptyBoxIsRefusedBeforeTheCodeIsCompared() {
        HttpSession session = new MockHttpSession();
        showPicture(session);

        assertThat(captcha.check(session, "   ")).isEqualTo(CaptchaService.CODE_MISSING);
        assertThat(captcha.check(session, null)).isEqualTo(CaptchaService.CODE_MISSING);
    }

    @Test
    void noCodeAtAllMeansNoPictureWasEverShown() {
        assertThat(captcha.check(new MockHttpSession(), "ABC123")).isEqualTo(CaptchaService.CODE_MISSING);
    }

    @Test
    void aCodeThatIsFiveMinutesOldIsWorthNothing() {
        HttpSession session = new MockHttpSession();
        String code = showCode(session);
        Challenge issued = challengeIn(session);
        // The same answer stamped as though it had been shown a minute too late,
        // because the age of a code is judged by when it was issued and nothing else.
        session.setAttribute(CaptchaService.CHALLENGE,
                new Challenge(issued.digest(), Instant.now().minus(CaptchaService.VALID_FOR.plusSeconds(1))));

        assertThat(captcha.check(session, code)).isEqualTo(CaptchaService.CODE_EXPIRED);
    }

    @Test
    void aFreshPictureRetiresTheCodeItReplaced() {
        HttpSession session = new MockHttpSession();
        String first = showCode(session);
        showPicture(session);

        assertThat(captcha.check(session, first)).isNotEqualTo(CaptchaService.PASSED);
    }

    @Test
    void theCodesAvoidLettersAndFiguresThatLookAlikeEachOther() {
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            seen.append(showCode(new MockHttpSession()));
        }

        assertThat(seen).hasSize(200 * 6);
        for (String ambiguous : new String[] {"I", "L", "O", "0", "1"}) {
            assertThat(seen.toString()).doesNotContain(ambiguous);
        }
    }

    @Test
    void theSessionHoldsNoAnswerAReaderOfSessionsCouldSignInWith() {
        HttpSession session = new MockHttpSession();
        showCode(session);
        String kept = challengeIn(session).digest();

        assertThat(kept).hasSize(64);
        // Reading the session and typing what is in it is refused: only the
        // characters on the screen are worth anything to anybody.
        assertThat(captcha.check(session, kept)).isEqualTo(CaptchaService.CODE_WRONG);
    }

    @Test
    void threeWrongCodesCostTheBrowserItsNextPicture() {
        HttpSession session = new MockHttpSession();
        showPicture(session);
        assertThat(captcha.check(session, "QQQQQQ")).isEqualTo(CaptchaService.CODE_WRONG);
        showPicture(session);
        assertThat(captcha.check(session, "QQQQQQ")).isEqualTo(CaptchaService.CODE_WRONG);

        assertThat(showPicture(session).isWaiting()).isFalse();
        assertThat(captcha.check(session, "QQQQQQ")).isEqualTo(CaptchaService.CODE_WRONG);
        assertThat(captcha.check(session, "WWWWWW")).isEqualTo(CaptchaService.CODE_WAIT);
        assertThat(captcha.newChallenge(session, ADDRESS).isWaiting()).isTrue();
    }

    @Test
    void aRightCodeClearsTheTallyThatWasCountingAgainstIt() {
        HttpSession session = new MockHttpSession();
        showPicture(session);
        captcha.check(session, "QQQQQQ");
        showPicture(session);
        captcha.check(session, "QQQQQQ");
        assertThat(captcha.check(session, showCode(session))).isEqualTo(CaptchaService.PASSED);

        // Two guesses and a fresh picture still leave this browser counting from
        // nothing; without the clear the third guess would have started a wait.
        assertThat(captcha.check(session, "QQQQQQ")).isEqualTo(CaptchaService.CODE_MISSING);
        assertThat(captcha.check(session, "QQQQQQ")).isEqualTo(CaptchaService.CODE_MISSING);
        assertThat(showPicture(session).isWaiting()).isFalse();
    }

    @Test
    void signInWithNoPictureAtAllIsCountedAgainstTheBrowserToo() {
        HttpSession session = new MockHttpSession();

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(captcha.check(session, "ABC123")).isEqualTo(CaptchaService.CODE_MISSING);
        }

        assertThat(session.getAttribute(CaptchaService.WAIT_UNTIL)).isNotNull();
        assertThat(captcha.check(session, "ABC123")).isEqualTo(CaptchaService.CODE_WAIT);
    }

    @Test
    void aBrowserSittingOutAWaitIsRefusedBeforeThePasswordIsLookedAt() {
        HttpSession session = new MockHttpSession();
        String code = showCode(session);
        captcha.check(session, "QQQQQQ");
        captcha.check(session, "QQQQQQ");
        captcha.check(session, "QQQQQQ");

        // The code this browser was really shown would have worked a minute ago;
        // the wait answers first, so the attempt never reaches the password.
        assertThat(captcha.check(session, code)).isEqualTo(CaptchaService.CODE_WAIT);
    }

    @Test
    void aWaitThatHasRunOutLetsTheBrowserAskAgain() {
        HttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaService.WAIT_UNTIL, Instant.now().minusSeconds(1));
        session.setAttribute(CaptchaService.WRONG_ANSWERS, 2);

        assertThat(showPicture(session).isWaiting()).isFalse();
        assertThat(session.getAttribute(CaptchaService.WAIT_UNTIL)).isNull();
        assertThat(session.getAttribute(CaptchaService.WRONG_ANSWERS)).isNull();
    }

    @Test
    void theCodesShownToOneBrowserAreCountedAndThenGivenBack() {
        CaptchaService impatient = service(3, 3, 120, 100_000);
        HttpSession session = new MockHttpSession();

        for (int picture = 1; picture <= 3; picture++) {
            assertThat(impatient.newChallenge(session, ADDRESS).isWaiting()).isFalse();
        }

        Picture fourth = impatient.newChallenge(session, ADDRESS);
        assertThat(fourth.isWaiting()).isTrue();
        assertThat(fourth.waitLeft()).isEqualTo(Duration.ofSeconds(120));
        assertThat(session.getAttribute(CaptchaService.CHALLENGE)).isNull();
        // The wait is the whole point of the count. After it the browser is
        // trusted again and the tally starts from nothing rather than from four.
        session.setAttribute(CaptchaService.WAIT_UNTIL, Instant.now().minusSeconds(1));
        for (int picture = 1; picture <= 3; picture++) {
            assertThat(impatient.newChallenge(session, ADDRESS).isWaiting()).isFalse();
        }
        assertThat(impatient.newChallenge(session, ADDRESS).isWaiting()).isTrue();
    }

    @Test
    void aPictureRefusedByAddressMintsNothingAndSpendsNothing() {
        CaptchaService counted = service(40, 3, 120, 2);
        HttpSession session = new MockHttpSession();
        assertThat(counted.newChallenge(session, ADDRESS).isWaiting()).isFalse();
        Picture second = counted.newChallenge(session, ADDRESS);

        Picture third = counted.newChallenge(session, ADDRESS);

        assertThat(third.isWaiting()).isTrue();
        assertThat(third.svg()).doesNotContain("aria-label=\"Security code to type\"");
        assertThat(session.getAttribute(CaptchaService.CODES_SHOWN)).isEqualTo(2);
        // A refused fetch mints no code and charges the browser nothing, but the
        // code it was really shown a moment ago is still there to be spent -
        // what is refused is the address, not the person standing at the screen.
        assertThat(counted.check(session, readCode(second.svg()))).isEqualTo(CaptchaService.PASSED);
        assertThat(counted.newChallenge(session, ADDRESS).isWaiting()).isTrue();
        assertThat(session.getAttribute(CaptchaService.CODES_SHOWN)).isEqualTo(2);
    }

    @Test
    void aWaitPictureSaysHowLongAndHoldsNoCode() {
        HttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaService.WAIT_UNTIL, Instant.now().plusSeconds(90));

        Picture picture = captcha.newChallenge(session, ADDRESS);

        assertThat(picture.isWaiting()).isTrue();
        assertThat(picture.waitLeft()).isBetween(Duration.ofSeconds(85), Duration.ofSeconds(90));
        assertThat(picture.svg()).contains("Too many codes asked for").contains("Try again in ");
        assertThat(picture.svg()).doesNotContain("aria-label=\"Security code to type\"");
    }

    @Test
    void thePictureIsDrawnAsCharactersAndNothingElseCanBeReadAsOne() {
        HttpSession session = new MockHttpSession();
        String picture = showPicture(session).svg();

        assertThat(GLYPH.matcher(picture).results().count()).isEqualTo(6);
        assertThat(picture).contains("pattern", "<path", "<circle")
                .doesNotContain("<script", "onload", "href=");
    }

    @Test
    void theCountsRefuseWithoutReachingTheDatabaseOrThePasswordAtAll() {
        HttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaService.WAIT_UNTIL, Instant.now().plusSeconds(60));

        // Nothing a waiting browser sends is even compared, right code or wrong,
        // so a wait cannot be argued out of by guessing well.
        assertThat(captcha.check(session, "ABC123")).isEqualTo(CaptchaService.CODE_WAIT);
        assertThat(captcha.check(session, null)).isEqualTo(CaptchaService.CODE_WAIT);
        assertThat(captcha.check(session, showCodeOrNull(session))).isEqualTo(CaptchaService.CODE_WAIT);
    }

    /** A session whose visitor has just been shown a code, and the picture they see. */
    private Picture showPicture(HttpSession session) {
        Picture picture = captcha.newChallenge(session, ADDRESS);
        assertThat(picture.isWaiting()).isFalse();
        return picture;
    }

    private String showCode(HttpSession session) {
        return readCode(showPicture(session).svg());
    }

    /** Draws a picture this browser may not be shown, to prove the wait outranks it. */
    private String showCodeOrNull(HttpSession session) {
        return captcha.newChallenge(session, ADDRESS).isWaiting() ? "QQQQQQ" : "no code needed";
    }

    private static String readCode(String svg) {
        Matcher glyphs = GLYPH.matcher(svg);
        StringBuilder code = new StringBuilder();
        while (glyphs.find()) {
            code.append(glyphs.group(1));
        }
        assertThat(code.toString()).hasSize(6);
        return code.toString();
    }

    private static Challenge challengeIn(HttpSession session) {
        assertThat(session.getAttribute(CaptchaService.CHALLENGE)).isInstanceOf(Challenge.class);
        return (Challenge) session.getAttribute(CaptchaService.CHALLENGE);
    }

    private static CaptchaService service(int codesPerSession, int wrongAnswersPerSession, long waitSeconds,
            int picturesPerMinute) {
        return new CaptchaService(new RequestThrottle(picturesPerMinute, Duration.ofMinutes(1)),
                codesPerSession, wrongAnswersPerSession, waitSeconds);
    }
}
