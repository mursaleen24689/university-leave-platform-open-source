package com.example.leaveplatform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The authenticator seed as it is kept: sealed with AES-GCM under a key the
 * database does not hold, so a stolen dump is not a bag of second factors.
 *
 * <p>A seed is not a password. A password is only ever compared, so
 * {@code password_hash} can be a one-way hash and nothing less. This has to be
 * read back every time the platform works out the number to expect, so it must
 * be reversible, and the answer to "reversible" is encryption with a key that
 * lives somewhere else.
 *
 * <p>This is the same construction as {@code mail.SecretCipher} - AES/GCM, a
 * random prefix so one seed sealed twice looks different twice, and a key
 * derived by hashing whatever phrase the university typed. It is deliberately a
 * second implementation rather than a shared one: the two secrets have different
 * keys, different sentences when opening fails, and different reasons for
 * existing, and keeping them apart is what lets either be rotated or replaced
 * without touching the other. {@code docs/DECISIONS.md} ADR-021 records that
 * choice and what it costs.
 *
 * <p>AES/GCM rather than CBC because it authenticates. A row edited by hand or
 * cut short fails to open instead of returning characters that look like a seed,
 * and a seed that looks right but is not one produces codes that are quietly
 * wrong forever.
 */
final class EnrolmentCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private EnrolmentCipher() {
    }

    static String seal(String seed, String keyMaterial) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(keyMaterial), new GCMParameterSpec(TAG_BITS, iv));
            byte[] closed = cipher.doFinal(seed.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[iv.length + closed.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(closed, 0, stored, iv.length, closed.length);
            return PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (Exception e) {
            throw new TwoStepRuleException("The authenticator seed could not be sealed. Is LEAVE_TWOSTEP_KEY set?");
        }
    }

    /**
     * The seed back again. Anything that was tampered with, truncated, sealed
     * under a different key or written by hand is refused here rather than
     * returned, and {@code TwoStepService} turns that refusal into a sign-in
     * that fails and a log line an administrator can act on.
     */
    static String open(String sealed, String keyMaterial) {
        if (sealed == null || !sealed.startsWith(PREFIX)) {
            throw new TwoStepRuleException("The saved authenticator seed is not readable.");
        }
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(keyMaterial), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new TwoStepRuleException("The saved authenticator seed could not be opened.");
        }
    }

    private static SecretKeySpec key(String keyMaterial) throws Exception {
        byte[] material = MessageDigest.getInstance("SHA-256")
                .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(material, "AES");
    }
}
