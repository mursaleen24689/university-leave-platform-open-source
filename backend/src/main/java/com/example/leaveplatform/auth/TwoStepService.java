package com.example.leaveplatform.auth;

import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.LoginAttempts;
import com.example.leaveplatform.reporting.QrGlyph;

/**
 * Two-step signing in, for the accounts whose owner has turned it on.
 *
 * <p>One account, one seed. The seed is handed to the person's authenticator
 * app once - as a picture to scan or the same text to type - sealed under
 * {@code LEAVE_TWOSTEP_KEY} and kept ever after, and from then on the app and
 * this platform each work out the same six-digit number from it and from the
 * clock. Nothing is sent to the app and nothing is received back except the
 * number, so a signed-in session an attacker has already stolen cannot be used
 * to enrol a device the person has never seen: turning this on costs a password
 * and a picture of a code at the same moment.
 *
 * <p>Three paths meet here, and they fail differently on purpose.
 *
 * <ul>
 *   <li><b>Enrolment</b> is self-service, needs the person's own password, and
 *   the seed is not saved until a code from the app proves the app has it. It
 *   is refused outright while {@code LEAVE_TWOSTEP_KEY} is missing, because a
 *   seed sealed under no key is a seed published to whoever holds a database
 *   copy.</li>
 *   <li><b>Signing in</b> is checked by {@code TwoStepEnforcementFilter} before
 *   the password is looked at, and its answer is one of four results that never
 *   reach the person as words: a wrong code returns the same page a wrong
 *   password returns, because saying which half failed would confirm the other
 *   half.</li>
 *   <li><b>Turning it off</b> needs the password and a live code when the
 *   person does it, and nothing at all when the administration team does it for
 *   somebody who has lost the phone - that is the only way back in, so it is
 *   the one action here that is not the person's own, and it is audited as
 *   such.</li>
 * </ul>
 *
 * <p>The seed of an account being enrolled right now lives in that browser's
 * session, not in the database, and only for as long as the screen says. A
 * person who walks away mid-enrolment leaves nothing behind to be confirmed
 * later, and a seed that was never confirmed is never stored.
 */
@Service
public class TwoStepService {

    /** The request parameter a sign-in attempt carries the app's number in. */
    public static final String CODE_PARAMETER = "otp";

    /** How many times one enrolment's code may be mistyped before the setup is thrown away. */
    static final int MAX_WRONG_CONFIRMATIONS = 3;

    /** Where an enrolment in progress is kept: this browser's own session, and nowhere else. */
    static final String PENDING_SEED = "app.two-step.seed";
    static final String PENDING_EXPIRES_AT = "app.two-step.expires-at";
    static final String PENDING_WRONG_CODES = "app.two-step.wrong-codes";

    private static final Logger log = LoggerFactory.getLogger(TwoStepService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TwoStepKey key;
    private final AuditService audit;
    private final String issuer;
    private final int digits;
    private final long periodSeconds;
    private final int windowSteps;
    private final long enrolmentMinutes;

    /** What the platform knows about one account's second step. */
    public record Status(boolean enabled, Instant since) {

        static final Status OFF = new Status(false, null);
    }

    /**
     * What the enrolment screen is shown: the seed, the same seed in
     * four-character groups for the app that will not scan a picture, the
     * address an app reads from a picture, the picture itself, and when this
     * offer lapses.
     */
    public record Enrolment(String seed, String seedForTyping, String otpAuthUri, String qrSvg,
            Instant expiresAt) {
    }

    /**
     * The four things a sign-in attempt's code can turn out to be. There is no
     * "wrong password" here: this is decided before the password is looked at,
     * and the answer is only ever used to choose between letting the attempt
     * continue and refusing it with the general message.
     */
    public enum CodeResult {
        /** The account has no second step, so the attempt is its owner's business. */
        NOT_ENROLLED,
        /** The number matches what the app should be showing right now. */
        ACCEPTED,
        /** Missing, mistyped or outside its window - and now counted. */
        REFUSED,
        /** The account is enrolled but this platform cannot read its seed, so nobody can sign in as it. */
        SEED_UNREADABLE
    }

    public TwoStepService(AppUserRepository users, PasswordEncoder passwordEncoder, TwoStepKey key,
            AuditService audit,
            @Value("${app.two-step.issuer:University Leave}") String issuer,
            @Value("${app.two-step.digits:6}") int digits,
            @Value("${app.two-step.period-seconds:30}") long periodSeconds,
            @Value("${app.two-step.window-steps:1}") int windowSteps,
            @Value("${app.two-step.enrolment-minutes:15}") long enrolmentMinutes) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.key = key;
        this.audit = audit;
        this.issuer = issuer;
        this.digits = digits;
        this.periodSeconds = periodSeconds;
        this.windowSteps = windowSteps;
        this.enrolmentMinutes = enrolmentMinutes;
    }

    /**
     * Where the seed of a setup in progress is kept: this browser's own
     * session, reached from the JSON endpoint through the servlet session and
     * from the Vaadin screen through Vaadin's wrapper of the same session. Both
     * write through to the one session the next sign-in attempt arrives on, and
     * neither makes this service know which framework it was called from.
     */
    public interface PendingStore {

        Object get(String key);

        void put(String key, Object value);

        void drop(String key);

        static PendingStore of(HttpSession session) {
            return new PendingStore() {
                @Override
                public Object get(String key) {
                    return session.getAttribute(key);
                }

                @Override
                public void put(String key, Object value) {
                    session.setAttribute(key, value);
                }

                @Override
                public void drop(String key) {
                    session.removeAttribute(key);
                }
            };
        }
    }

    @Transactional(readOnly = true)
    public Status status(String username) {
        return users.findByUsername(username)
                .map(user -> user.hasTwoStep()
                        ? new Status(true, user.getTwoFactorEnrolledAt())
                        : Status.OFF)
                .orElse(Status.OFF);
    }

    /**
     * Starts an enrolment: the password is checked, a fresh seed is generated
     * and held in this browser's session, and the screen gets what it needs to
     * put that seed in front of an app. Nothing is written to the database yet.
     */
    @Transactional(readOnly = true)
    public Enrolment beginEnrolment(String username, String password, PendingStore pending) {
        require(key.isPresent(),
                "This platform cannot set up two-step signing until its own key is configured."
                        + " Ask the administration team about LEAVE_TWOSTEP_KEY.");
        AppUser user = account(username);
        require(!user.isMustChangePassword(),
                "Choose your own password first; two-step is set up on an account that has one.");
        require(!user.hasTwoStep(),
                "Two-step signing is already on for your account. Turn it off before you set up"
                        + " a different phone.");
        require(password != null && !password.isBlank(), "Type your current password.");
        require(passwordEncoder.matches(password, user.getPasswordHash()),
                "That is not your current password.");

        String seed = OneTimeCodes.newSeed();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(enrolmentMinutes));
        pending.put(PENDING_SEED, seed);
        pending.put(PENDING_EXPIRES_AT, expiresAt);
        pending.drop(PENDING_WRONG_CODES);
        String uri = otpAuthUri(username, seed);
        return new Enrolment(seed, OneTimeCodes.grouped(seed), uri,
                // 60 millimetres across: a phone scans a screen comfortably at
                // that size, and a smaller picture is what makes somebody give
                // up and type the seed.
                QrGlyph.svg(uri, 60, "Scan to add this account to your authenticator app"), expiresAt);
    }

    /**
     * Finishes an enrolment. The code proves the app really got the seed -
     * without that check a mistyped seed would sit in the database looking
     * enrolled and refuse every sign-in forever, which is the one failure an
     * authenticator setup has to make impossible.
     */
    @Transactional
    public Status confirmEnrolment(String username, String typedCode, PendingStore pending) {
        AppUser user = account(username);
        require(!user.hasTwoStep(), "Two-step signing is already on for your account.");
        Object stored = pending.get(PENDING_SEED);
        Object expiry = pending.get(PENDING_EXPIRES_AT);
        if (!(stored instanceof String seed)) {
            throw new TwoStepRuleException("Nothing is waiting to be confirmed. Start the setup again.");
        }
        if (!(expiry instanceof Instant until) || !Instant.now().isBefore(until)) {
            clear(pending);
            throw new TwoStepRuleException("That setup had timed out. Start it again and type the code"
                    + " within " + enrolmentMinutes + " minutes.");
        }
        require(key.isPresent(), "This platform cannot store an authenticator seed without LEAVE_TWOSTEP_KEY.");

        if (!OneTimeCodes.matches(seed, typedCode, Instant.now(), periodSeconds, digits, windowSteps)) {
            int wrong = wrongAnswers(pending) + 1;
            if (wrong >= MAX_WRONG_CONFIRMATIONS) {
                clear(pending);
                throw new TwoStepRuleException("That code did not match three times, so this setup has been"
                        + " thrown away. Start again with the app open in front of you.");
            }
            pending.put(PENDING_WRONG_CODES, wrong);
            throw new TwoStepRuleException("That is not the code your app is showing. Check the time on the"
                    + " phone, wait for the number to change, and try once more.");
        }

        Instant now = Instant.now();
        user.enrolTwoStep(EnrolmentCipher.seal(seed, key.material()), now);
        clear(pending);
        audit.recordAs(username, AuditAction.TWO_STEP_ENABLED, "ACCOUNT", user.getId(),
                "Turned on two-step signing with an authenticator app.");
        return new Status(true, now);
    }

    /**
     * Turns the second step off, by its owner. Both halves are needed: the
     * password proves who is asking, and a live code proves the phone is still
     * in their hand. Without the code, a stolen signed-in browser could quietly
     * remove the second factor and leave the account looking protected when it
     * no longer was - and a person changing phones has their app open anyway.
     */
    @Transactional
    public void turnOff(String username, String password, String typedCode) {
        AppUser user = account(username);
        require(user.hasTwoStep(), "Two-step signing is not on for your account.");
        require(password != null && !password.isBlank(), "Type your current password.");
        require(passwordEncoder.matches(password, user.getPasswordHash()),
                "That is not your current password.");
        require(typedCode != null && !typedCode.isBlank(),
                "Type the code your app is showing, so this platform knows the phone is yours.");
        require(OneTimeCodes.matches(unsealed(user), typedCode, Instant.now(), periodSeconds, digits, windowSteps),
                "That is not the code your app is showing.");

        user.clearTwoStep();
        audit.recordAs(username, AuditAction.TWO_STEP_DISABLED, "ACCOUNT", user.getId(),
                "Turned off two-step signing; the account now needs its password alone.");
    }

    /**
     * What a sign-in attempt is worth, decided before Spring Security reads the
     * password, and counted in the same total as a wrong password so one limit
     * caps guessing at either half.
     *
     * <p>A seed this platform cannot open is the one result that is not the
     * person's own failure. It fails closed - nobody signs in as that account -
     * and it is written to the log rather than to the audit trail, because
     * whoever does not know the password can ask for it repeatedly, and an
     * auditor's table is not the place for that noise. The administration team
     * turns two-step off for the account afterwards, which is the recovery, and
     * the log line says so.
     */
    @Transactional
    public CodeResult checkSignInCode(String username, String typedCode) {
        if (username == null || username.isBlank()) {
            return CodeResult.NOT_ENROLLED;
        }
        AppUser user = users.findByUsername(username).orElse(null);
        if (user == null || !user.hasTwoStep()) {
            return CodeResult.NOT_ENROLLED;
        }
        String seed;
        try {
            seed = unsealed(user);
        } catch (TwoStepRuleException unreadable) {
            log.error("The authenticator seed of account {} cannot be opened ({}). Nobody can sign in as"
                    + " this account until the administration team turns two-step off for it: {}",
                    username, unreadable.getMessage(), "TWO_STEP_SEED_UNREADABLE");
            return CodeResult.SEED_UNREADABLE;
        }
        if (OneTimeCodes.matches(seed, typedCode, Instant.now(), periodSeconds, digits, windowSteps)) {
            return CodeResult.ACCEPTED;
        }
        user.registerFailedAttempt(LoginAttempts.MAX_FAILURES, LoginAttempts.LOCK_DURATION);
        return CodeResult.REFUSED;
    }

    /**
     * The address an authenticator app reads a seed from. The shape is the
     * published one: a scheme, the account the code belongs to, and the few
     * parameters an app needs to reproduce the maths - issuer, digits, period,
     * algorithm. Apps disagree about everything else, so nothing else goes in,
     * and every part that can hold a name or a space is encoded.
     */
    String otpAuthUri(String username, String seed) {
        String label = encoded(issuer + ":" + username);
        return "otpauth://totp/" + label
                + "?secret=" + seed
                + "&issuer=" + encoded(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    private String unsealed(AppUser user) {
        return EnrolmentCipher.open(user.getTwoFactorSecret(), key.material());
    }

    private AppUser account(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new TwoStepRuleException(
                        "This screen needs a signed-in account of its own."));
    }

    private static String encoded(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new TwoStepRuleException(message);
        }
    }

    private static int wrongAnswers(PendingStore pending) {
        Object counted = pending.get(PENDING_WRONG_CODES);
        return counted instanceof Integer times ? times : 0;
    }

    private static void clear(PendingStore pending) {
        pending.drop(PENDING_SEED);
        pending.drop(PENDING_EXPIRES_AT);
        pending.drop(PENDING_WRONG_CODES);
    }
}
