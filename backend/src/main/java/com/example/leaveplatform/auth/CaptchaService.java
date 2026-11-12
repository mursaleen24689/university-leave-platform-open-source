package com.example.leaveplatform.auth;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.leaveplatform.limit.RequestThrottle;

/**
 * The security code on the sign-in screen, so a bot cannot try passwords without
 * proving it saw a picture first.
 *
 * <p>Ordering is the whole point of the filter in front of this service, and
 * counting is the whole point of this one. Three counts sit behind the picture,
 * each answering a different kind of caller:
 * <ul>
 * <li><b>per address</b> - how many pictures this address is shown in a minute,
 * which is the ceiling on how fast anyone can create a session to knock on the
 * door. It lives in {@link RequestThrottle}, not in the session, because a
 * stranger who has not been given a session yet is exactly who it is about.</li>
 * <li><b>per browser</b> - how many pictures one session may be shown before it
 * has to wait, so that one visitor cannot ask for codes forever.</li>
 * <li><b>per wrong answer</b> - how many codes one session may get wrong before
 * it has to wait, which is the one a script guessing at a picture runs into
 * first.</li>
 * </ul>
 * A wait is a wait: during one the session is shown no new picture and its
 * sign-in attempts are refused before the password is looked at, so the count
 * cannot be spent while the clock runs.
 *
 * <p>The answer lives in the visitor's own HTTP session, which is why there is no
 * server-side table of codes to grow without limit or sweep clean. The same
 * session is what Spring Security's login filter reads a few milliseconds later,
 * so the check and the sign-in are about one person. What the session holds is a
 * keyed digest of the answer and never the answer, so a session written to a log,
 * a debug endpoint or a heap dump does not hand out live codes.
 *
 * <p>Every code is used once. Showing a picture issues the answer, and
 * {@link #check} throws that answer away whatever the visitor typed, so one
 * picture buys one sign-in attempt and a failed password costs a fresh code.
 */
@Service
public class CaptchaService {

    static final String CHALLENGE = "com.example.captchaChallenge";
    static final String CODES_SHOWN = "com.example.captchaCodesShown";
    static final String WRONG_ANSWERS = "com.example.captchaWrongAnswers";
    static final String WAIT_UNTIL = "com.example.captchaWaitUntil";
    static final Duration VALID_FOR = Duration.ofMinutes(5);

    /** Request parameter the typed code travels in, inside the sign-in form. */
    public static final String CODE_PARAMETER = "captcha";
    /** Query parameter that tells the screen why a sign-in attempt came back. */
    public static final String REASON_PARAMETER = "code";
    /** The reason a check accepted the code; nothing else continues to the password. */
    public static final String PASSED = "passed";
    public static final String CODE_MISSING = "missing";
    public static final String CODE_WRONG = "wrong";
    public static final String CODE_EXPIRED = "expired";
    /** This browser guessed so often that it is sitting out a wait. */
    public static final String CODE_WAIT = "wait";

    /** No I, L, O or 0 and 1, which a tired reader cannot tell apart in a noisy picture. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final int WIDTH = 220;
    private static final int HEIGHT = 68;

    private final RequestThrottle pictureLimit;
    private final int codesPerSession;
    private final int wrongAnswersPerSession;
    private final Duration waitAfterTooMany;

    private final SecureRandom random = new SecureRandom();

    /**
     * What keeps a session's digest from being a list of codes in disguise. The
     * space is small enough - one code in 32<sup>6</sup> - that an unprotected
     * hash of it is guessable at a stroke, so the hash is keyed, and the key is
     * random per process because codes only ever live as long as the session that
     * holds them. A restart retires outstanding codes, which costs nobody: the
     * person types a fresh picture.
     */
    private final byte[] answerKey = new byte[32];

    public CaptchaService(RequestThrottle captchaPictureLimit,
            @Value("${app.sign-in.code-pictures-per-session:40}") int codesPerSession,
            @Value("${app.sign-in.wrong-codes-per-session:3}") int wrongAnswersPerSession,
            @Value("${app.sign-in.wait-after-too-many-seconds:120}") long waitSeconds) {
        if (codesPerSession < 1 || wrongAnswersPerSession < 1 || waitSeconds < 1) {
            throw new IllegalArgumentException("The security-code counts have to allow at least one of each.");
        }
        this.pictureLimit = captchaPictureLimit;
        this.codesPerSession = codesPerSession;
        this.wrongAnswersPerSession = wrongAnswersPerSession;
        this.waitAfterTooMany = Duration.ofSeconds(waitSeconds);
        random.nextBytes(answerKey);
    }

    record Challenge(String digest, Instant issuedAt) {
    }

    /**
     * What the picture endpoint gives a visitor: either the code to read, with no
     * wait named, or the length of the wait they have earned, with a picture that
     * says so instead of a code.
     */
    public record Picture(String svg, Duration waitLeft) {

        public boolean isWaiting() {
            return waitLeft != null;
        }
    }

    /**
     * A fresh code for this visitor, as the picture they are asked to read, or
     * the wait standing in its place.
     *
     * <p>Nothing is issued while a count is spent, and no count is spent while
     * nothing is issued: a visitor who is refused has not used up one of the
     * pictures their session may still be shown, and a refused address has not
     * bought a live code it could sign in with.
     */
    public Picture newChallenge(HttpSession session, String address) {
        Instant now = Instant.now();
        Duration sessionWait = waitLeft(session, now);
        if (sessionWait != null) {
            return new Picture(waitSvg(sessionWait), sessionWait);
        }
        Optional<Duration> refused = pictureLimit.refusalFor(address);
        if (refused.isPresent()) {
            return new Picture(waitSvg(refused.get()), refused.get());
        }
        int codesShown = countIn(session, CODES_SHOWN) + 1;
        if (codesShown > codesPerSession) {
            return new Picture(waitSvg(startWait(session, now)), waitAfterTooMany);
        }
        session.setAttribute(CODES_SHOWN, codesShown);
        String answer = randomAnswer();
        session.setAttribute(CHALLENGE, new Challenge(digestOf(answer), now));
        return new Picture(svg(answer), null);
    }

    /**
     * Why a sign-in attempt does or does not answer the code that is on screen.
     * The code is dropped whatever the outcome, so a guess cannot be retried
     * against the same picture, and an empty box is refused before the answer
     * is looked at.
     *
     * <p>Every outcome except a right code is counted, including the ones that
     * had no code to answer at all: a script that posts sign-ins it never saw a
     * picture for is the behaviour the count is there to end.
     */
    public String check(HttpSession session, String typed) {
        Instant now = Instant.now();
        if (waitLeft(session, now) != null) {
            return CODE_WAIT;
        }
        if (!(session.getAttribute(CHALLENGE) instanceof Challenge challenge)) {
            registerWrongAnswer(session, now);
            return CODE_MISSING;
        }
        session.removeAttribute(CHALLENGE);
        if (challenge.issuedAt().plus(VALID_FOR).isBefore(now)) {
            registerWrongAnswer(session, now);
            return CODE_EXPIRED;
        }
        if (typed == null || typed.isBlank()) {
            registerWrongAnswer(session, now);
            return CODE_MISSING;
        }
        // Compared as bytes of equal length, so the time an answer takes tells a
        // guesser nothing about how close it came.
        if (MessageDigest.isEqual(challenge.digest().getBytes(StandardCharsets.UTF_8),
                digestOf(typed).getBytes(StandardCharsets.UTF_8))) {
            session.removeAttribute(WRONG_ANSWERS);
            return PASSED;
        }
        registerWrongAnswer(session, now);
        return CODE_WRONG;
    }

    /**
     * How long this browser still has to sit out, or null when it may be shown a
     * code again. An expired wait is cleared as it is read, along with the
     * guesses that caused it, so a visitor who waits it out starts level again.
     */
    private Duration waitLeft(HttpSession session, Instant now) {
        if (!(session.getAttribute(WAIT_UNTIL) instanceof Instant until)) {
            return null;
        }
        if (until.isAfter(now)) {
            return Duration.between(now, until);
        }
        session.removeAttribute(WAIT_UNTIL);
        session.removeAttribute(WRONG_ANSWERS);
        return null;
    }

    /**
     * Ends this browser's run of codes and starts its wait, clearing the counters
     * that the wait makes pointless and the code that was issued before it.
     */
    private Duration startWait(HttpSession session, Instant now) {
        session.removeAttribute(CODES_SHOWN);
        session.removeAttribute(WRONG_ANSWERS);
        session.removeAttribute(CHALLENGE);
        session.setAttribute(WAIT_UNTIL, now.plus(waitAfterTooMany));
        return waitAfterTooMany;
    }

    private void registerWrongAnswer(HttpSession session, Instant now) {
        int wrongAnswers = countIn(session, WRONG_ANSWERS) + 1;
        if (wrongAnswers >= wrongAnswersPerSession) {
            startWait(session, now);
            return;
        }
        session.setAttribute(WRONG_ANSWERS, wrongAnswers);
    }

    private static int countIn(HttpSession session, String attribute) {
        return session.getAttribute(attribute) instanceof Integer count ? count : 0;
    }

    private String randomAnswer() {
        StringBuilder answer = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            answer.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return answer.toString();
    }

    /**
     * The keyed digest of an answer, in the one shape both halves of the platform
     * agree on: trimmed, capitals, as the person reads and types it. Storing
     * digests is what the case-insensitive check used to do by comparing text,
     * and it still does it - a code typed in lower case has to match a picture
     * drawn in capitals.
     */
    private String digestOf(String answer) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(answerKey, "HmacSHA256"));
            byte[] keyed = mac.doFinal(normalise(answer).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(keyed.length * 2);
            for (byte part : keyed) {
                hex.append(Character.forDigit((part >> 4) & 0xF, 16)).append(Character.forDigit(part & 0xF, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("This platform cannot hash a security code.", impossible);
        }
    }

    private static String normalise(String answer) {
        return answer.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * The picture: each character sits at its own height, tilt and size over a
     * hatched background, waves and specks, with more of them drawn again on top
     * of the characters. It is drawn as text rather than as an image file so the
     * platform needs no graphics library and the code still reaches the browser
     * as it was generated here.
     *
     * <p>One rule holds the drawing together: a character is a {@code text}
     * element and nothing else is. Every reader of this picture - the sign-in
     * screen's tests, the checks under {@code docs/TEST-PLAN.md}, the local proxy
     * used for a browser pass - takes the characters out of the text elements in
     * order, and a decoration written as text would be read as part of a code.
     */
    private String svg(String answer) {
        StringBuilder glyphs = new StringBuilder();
        for (int i = 0; i < answer.length(); i++) {
            int left = 8 + i * 34 + random.nextInt(4);
            int baseline = (i % 2 == 0 ? 37 : 46) + random.nextInt(7) - 3;
            int tilt = random.nextInt(45) - 22;
            int size = 27 + random.nextInt(7);
            glyphs.append("<text x=\"").append(left).append("\" y=\"").append(baseline)
                    .append("\" transform=\"rotate(").append(tilt).append(' ').append(left + 9)
                    .append(' ').append(baseline).append(")\" fill=\"hsl(")
                    .append(200 + random.nextInt(45)).append(", ").append(30 + random.nextInt(35))
                    .append("%, ").append(14 + random.nextInt(24)).append("%)\"")
                    .append(" font-family=\"monospace\" font-size=\"").append(size)
                    .append("\" font-weight=\"700\">").append(answer.charAt(i)).append("</text>");
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + WIDTH + "\" height=\"" + HEIGHT
                + "\" viewBox=\"0 0 " + WIDTH + " " + HEIGHT + "\" role=\"img\""
                + " aria-label=\"Security code to type\">"
                + hatching() + squiggle(2.5, "0.20") + lines(10) + specks(26)
                + "<rect width=\"" + WIDTH + "\" height=\"" + HEIGHT + "\" fill=\"none\" stroke=\"rgba(15,23,42,0.10)\"/>"
                + glyphs + squiggle(2, "0.26") + lines(4) + specks(14) + "</svg>";
    }

    /**
     * A set of slanted lines behind the characters, tilted by whichever way round
     * the gap falls out. Fine enough to read through, close enough that a machine
     * reading the picture cannot tell a stroke of the hatch from a stroke of a
     * letter without looking at the letter.
     */
    private String hatching() {
        int gap = 5 + random.nextInt(4);
        return "<defs><pattern id=\"app-captcha-hatch\" width=\"" + gap + "\" height=\"" + HEIGHT
                + "\" patternUnits=\"userSpaceOnUse\" patternTransform=\"skewX("
                + (random.nextInt(25) - 12) + ")\"><path d=\"M0 " + HEIGHT + " L0 0\" stroke=\"rgba(15,23,42,0.09)"
                + "\" stroke-width=\"1\"/></pattern></defs><rect width=\"" + WIDTH + "\" height=\"" + HEIGHT
                + "\" fill=\"#f1f5f9\"/><rect width=\"" + WIDTH + "\" height=\"" + HEIGHT
                + "\" fill=\"url(#app-captcha-hatch)\"/>";
    }

    /** One wave across the picture, drawn smooth from four reflected arcs. */
    private String squiggle(double width, String opacity) {
        int start = 12 + random.nextInt(HEIGHT - 24);
        int step = WIDTH / 4;
        StringBuilder curve = new StringBuilder("<path d=\"M0 ").append(start).append(" Q").append(step / 2)
                .append(' ').append(clamp(start + random.nextInt(31) - 15)).append(' ').append(step).append(' ')
                .append(clamp(start + random.nextInt(21) - 10));
        for (int i = 1; i < 4; i++) {
            curve.append(" T").append(step * (i + 1)).append(' ').append(clamp(random.nextInt(HEIGHT)));
        }
        return curve.append("\" stroke=\"rgba(15,23,42,").append(opacity).append("\" stroke-width=\"")
                .append(width).append("\" fill=\"none\" stroke-linecap=\"round\"/>").toString();
    }

    private String lines(int howMany) {
        StringBuilder noise = new StringBuilder();
        for (int i = 0; i < howMany; i++) {
            noise.append("<line x1=\"").append(random.nextInt(WIDTH)).append("\" y1=\"").append(clamp(random.nextInt(HEIGHT)))
                    .append("\" x2=\"").append(random.nextInt(WIDTH)).append("\" y2=\"").append(clamp(random.nextInt(HEIGHT)))
                    .append("\" stroke=\"rgba(15,23,42,0.18)\" stroke-width=\"1\"/>");
        }
        return noise.toString();
    }

    private String specks(int howMany) {
        StringBuilder noise = new StringBuilder();
        for (int i = 0; i < howMany; i++) {
            noise.append("<circle cx=\"").append(random.nextInt(WIDTH)).append("\" cy=\"").append(clamp(random.nextInt(HEIGHT)))
                    .append("\" r=\"").append(random.nextBoolean() ? "1.3" : "2").append("\" fill=\"rgba(15,23,42,0.15)\"/>");
        }
        return noise.toString();
    }

    /** Keeps a random mark inside the paper, so nothing is drawn where nobody can see it. */
    private int clamp(int position) {
        return Math.max(1, Math.min(HEIGHT - 1, position));
    }

    /**
     * The picture that stands in for a code while a count is spent. It says what
     * happened and how long to wait, because a grey box gives the person nothing
     * to do except click again - which is the behaviour the box is there to stop.
     *
     * <p>It is not a code and holds none: no caller reading this picture can sign
     * in with it, and the endpoint that returns it answers 429 with a
     * {@code Retry-After}, so a script is told in the language scripts read.
     */
    private String waitSvg(Duration wait) {
        long seconds = Math.max(1, wait.toSeconds());
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + WIDTH + "\" height=\"" + HEIGHT
                + "\" viewBox=\"0 0 " + WIDTH + " " + HEIGHT + "\" role=\"img\""
                + " aria-label=\"Wait before the next security code\">"
                + "<rect width=\"" + WIDTH + "\" height=\"" + HEIGHT + "\" fill=\"#eef2f7\"/>"
                + "<text x=\"14\" y=\"28\" font-family=\"monospace\" font-size=\"13\" fill=\"#334155\">"
                + "Too many codes asked for.</text>"
                + "<text x=\"14\" y=\"48\" font-family=\"monospace\" font-size=\"13\" fill=\"#334155\">"
                + "Try again in " + seconds + " seconds.</text></svg>";
    }
}
