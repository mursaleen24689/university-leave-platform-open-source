package com.example.leaveplatform.reporting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The short code that lets a printed leave form be checked from outside the
 * platform.
 *
 * <p>The earlier application put a QR on the same form whose text was the
 * applicant's name, the start date, the day count and the status joined with
 * pipes. Nothing checks it: the picture reads out exactly what the paper
 * already says, so a forged form and a genuine one produce the same scan. Here
 * the code cannot be worked out from what is printed on the form - it is keyed
 * by {@link VerificationKey}, which lives only in the server's environment - so
 * a reader who does not hold that key can copy a number but cannot produce a
 * code that passes.
 *
 * <p>Two shapes of payload, decided by one setting. With {@code app.public-url}
 * configured the QR holds an address a phone opens by itself; without it, which
 * is how the platform runs today, the QR holds the same two parts in one short
 * line a person can type at the desk. Both carry the reference and the code, and
 * both are read by the same check.
 *
 * <p>Without a key the platform refuses rather than guesses: no code is minted,
 * nothing checks, and the printed form says in words that this server cannot
 * verify. A default key would be worse than none, because every form ever
 * printed would then be checkable by anybody who read the source.
 */
@Component
public class VerificationCode {

    /** Names the code in the printed line, so a scan says what it is holding. */
    public static final String MARKER = "LEAVE-VERIFY";

    /** The endpoint a public address points at. */
    public static final String VERIFY_PATH = "/api/v1/reports/verify";

    /**
     * Six bytes of the hash, shown as twelve characters of 0-9 and a-f. Long
     * enough that a guess has one chance in 6.9e14, short enough to type from
     * paper at a desk.
     */
    private static final int CODE_BYTES = 6;

    /** What a reference looks like: the prefix the database stamps, then digits. */
    private static final java.util.regex.Pattern REFERENCE =
            java.util.regex.Pattern.compile("LV-\\d{1,20}");

    private final VerificationKey key;
    private final String publicUrl;

    public VerificationCode(VerificationKey key, @Value("${app.public-url:}") String publicUrl) {
        this.key = key;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim();
    }

    /** Whether this server can sign and check codes at all. */
    public boolean isConfigured() {
        return key.isPresent();
    }

    /** The code for one request, or a refusal that says why there is none. */
    public String codeFor(String reference) {
        requireKey();
        return hex(reference).substring(0, CODE_BYTES * 2);
    }

    /**
     * What goes into the picture: an address a phone can open when the platform
     * has been told where it lives, and the same two parts as plain words when
     * it has not. The number is always printed as letters beside the picture, so
     * a form printed today stays readable if the address changes next year.
     */
    public String payloadFor(String reference) {
        String code = codeFor(reference);
        if (publicUrl.isBlank()) {
            return MARKER + "|" + reference + "|" + code;
        }
        return trimTrailingSlash(publicUrl) + VERIFY_PATH
                + "?reference=" + reference + "&code=" + code;
    }

    /**
     * Whether a code was minted by this server for this reference. A malformed
     * number or code is answered the same way as a wrong one, because the only
     * thing a caller learns from a refusal is that they do not have a valid pair.
     */
    public boolean matches(String reference, String code) {
        if (!key.isPresent() || reference == null || code == null) {
            return false;
        }
        String shaped = reference.trim().toUpperCase();
        String given = code.trim().toLowerCase();
        if (!REFERENCE.matcher(shaped).matches() || !given.matches("[0-9a-f]{" + CODE_BYTES * 2 + "}")) {
            return false;
        }
        String expected = hex(shaped).substring(0, CODE_BYTES * 2);
        // MessageDigest.isEqual compares without leaking how far the two agreed,
        // so timing cannot be used to walk a code towards a match one character
        // at a time.
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8));
    }

    private String hex(String reference) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.material().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().withLowerCase()
                    .formatHex(mac.doFinal((MARKER + "|" + reference).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            // HMAC-SHA256 is part of every Java runtime this platform runs on.
            throw new IllegalStateException("This server cannot sign verification codes.");
        }
    }

    private void requireKey() {
        if (!key.isPresent()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "This server has no LEAVE_VERIFY_KEY, so it cannot issue or check verification"
                            + " codes. Ask the administration team to set one; forms still print, with"
                            + " the number in letters and no code.");
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
