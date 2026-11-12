package com.example.leaveplatform.auth;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The maths of a six-digit code: RFC 4648 Base32 for the seed an authenticator
 * app is given, and RFC 6238 for the number both sides work out from it.
 *
 * <p>Written here rather than pulled from a library for two reasons. The
 * platform builds offline, from a local repository, and a new dependency is a
 * download that has to be found, reviewed and kept current on every machine
 * that deploys this. And the whole standard is this small: a keyed hash of an
 * eight-byte counter, five bytes of it folded into a number. Anything bigger
 * would be hiding a decision this project should make on purpose and in the
 * open - which hash, how many digits, how long a step, how much clock skew to
 * forgive - and those four numbers are what a deployment has to be able to read
 * in one file and match against the app it is asking people to install.
 *
 * <p>The seed is never compared in the clear and never logged. A code typed by
 * a person is compared to the code this server computes, in constant time, so
 * neither the answer nor a timing difference is available to whoever typed it.
 */
final class OneTimeCodes {

    /**
     * 160 bits, the length RFC 6238 asks for with SHA-1 and the smallest seed
     * worth having: a shorter one is a shorter space for anybody who copies the
     * database and works at it offline.
     */
    private static final int SEED_BYTES = 20;

    /**
     * 128 bits, the shortest seed RFC 6238 will accept. A seed that opens to
     * fewer bytes was either typed wrong or edited by hand in a database
     * console, and computing codes against it would give an attacker a smaller
     * space to work in while every sign-in still looked like a wrong code.
     */
    private static final int MIN_SEED_BYTES = 16;

    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /** The value each Base32 character stands for, or -1 for a character that is not one. */
    private static final int[] BASE32_VALUES = new int[128];

    static {
        Arrays.fill(BASE32_VALUES, -1);
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            BASE32_VALUES[BASE32_ALPHABET[i]] = i;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimeCodes() {
    }

    /** A fresh seed, as the upper-case Base32 text an authenticator app expects. */
    static String newSeed() {
        byte[] seed = new byte[SEED_BYTES];
        RANDOM.nextBytes(seed);
        return encode(seed);
    }

    /**
     * The seed in four-character groups, which is how a person typing it by
     * hand reads it back correctly. Authenticator apps ignore the spaces; this
     * is for the screen that shows the seed.
     */
    static String grouped(String base32) {
        StringBuilder grouped = new StringBuilder(base32.length() + base32.length() / 4);
        for (int i = 0; i < base32.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                grouped.append(' ');
            }
            grouped.append(base32.charAt(i));
        }
        return grouped.toString();
    }

    static String encode(byte[] seed) {
        StringBuilder text = new StringBuilder((seed.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : seed) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                text.append(BASE32_ALPHABET[(buffer >>> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            text.append(BASE32_ALPHABET[(buffer << (5 - bits)) & 0x1f]);
        }
        return text.toString();
    }

    /**
     * The seed as bytes. Spaces, hyphens and lower case are forgiven because a
     * person copying a seed off a screen puts them in; a character outside the
     * alphabet is refused rather than read as something else, because a seed
     * quietly decoded wrong produces codes that are quietly wrong forever.
     */
    static byte[] decode(String base32) {
        String cleaned = base32.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("The seed is empty.");
        }
        int[] digits = new int[cleaned.length()];
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            int value = c < BASE32_VALUES.length ? BASE32_VALUES[c] : -1;
            if (value < 0) {
                throw new IllegalArgumentException("That is not an authenticator seed: " + c + " is not one of its characters.");
            }
            digits[i] = value;
        }
        byte[] seed = new byte[digits.length * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int written = 0;
        for (int digit : digits) {
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                seed[written++] = (byte) ((buffer >>> (bits - 8)) & 0xff);
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        if (seed.length < MIN_SEED_BYTES) {
            throw new IllegalArgumentException("That authenticator seed is too short to sign in with.");
        }
        return seed;
    }

    /**
     * The code the holder of this seed should be seeing at this instant.
     *
     * @param periodSeconds how long one code is current, normally 30
     * @param digits how long the answer is, normally 6
     */
    static String at(String base32Seed, Instant when, long periodSeconds, int digits) {
        long step = when.getEpochSecond() / periodSeconds;
        return hotp(decode(base32Seed), step, digits);
    }

    /**
     * Whether {@code typed} is the code this seed gives now, or gave one step
     * either side.
     *
     * <p>The window exists because a phone and a server do not have the same
     * clock and neither has the same idea of when a minute turned over. One step
     * either way is the accepted compromise: it forgives up to a minute of skew
     * in both directions, which is more than a properly set phone drifts, and it
     * keeps a captured code useful for minutes rather than hours. Every extra
     * step accepted is that much longer a stolen number stays valid, so this
     * stays at one and is not a knob to turn upwards.
     */
    static boolean matches(String base32Seed, String typed, Instant when, long periodSeconds,
            int digits, int windowSteps) {
        if (typed == null) {
            return false;
        }
        String cleaned = typed.trim().replace(" ", "").replace("-", "");
        if (cleaned.length() != digits || !isAllDigits(cleaned)) {
            return false;
        }
        byte[] wanted = cleaned.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] seed = decode(base32Seed);
        long step = when.getEpochSecond() / periodSeconds;
        boolean matched = false;
        for (int offset = -windowSteps; offset <= windowSteps; offset++) {
            byte[] candidate = hotp(seed, step + offset, digits)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            // Every step is compared even after a match: returning early would
            // make the time depend on which step was right, which is a fact
            // about the seed nobody standing at the screen is owed.
            matched = MessageDigest.isEqual(wanted, candidate) || matched;
        }
        return matched;
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /** RFC 4226 dynamic truncation: an offset from the hash's last byte picks four of its bytes. */
    private static String hotp(byte[] seed, long counter, int digits) {
        byte[] hash = hmac(seed, ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int modulus = (int) Math.pow(10, digits);
        return zeroPad(binary % modulus, digits);
    }

    private static byte[] hmac(byte[] seed, byte[] counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(seed, "HmacSHA1"));
            return mac.doFinal(counter);
        } catch (Exception e) {
            throw new IllegalStateException("This platform cannot compute sign-in codes.", e);
        }
    }

    private static String zeroPad(int value, int digits) {
        String text = Integer.toString(value);
        return text.length() >= digits
                ? text.substring(text.length() - digits)
                : "0".repeat(digits - text.length()) + text;
    }
}
