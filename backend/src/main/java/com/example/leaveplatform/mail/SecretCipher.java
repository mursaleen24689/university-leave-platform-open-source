package com.example.leaveplatform.mail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The mail password as it is kept: encrypted with a key the database does not
 * hold, so a stolen dump is not a mail account and a readable settings screen is
 * not a readable password.
 *
 * <p>The key comes from {@code LEAVE_MAIL_KEY} and only from there. It is hashed to
 * the size AES wants, so the university can type a phrase of any length. With no
 * key set, saving a password is refused rather than quietly stored in the clear -
 * the rest of the settings still save, because a host name is not a secret and a
 * person can finish the job once the key exists.
 *
 * <p>AES/GCM rather than CBC because it authenticates: a row edited by hand, or
 * cut short, fails to decrypt instead of returning characters that look like a
 * password and are not one. The random prefix means the same password typed twice
 * is stored twice differently.
 */
final class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private SecretCipher() {
    }

    static boolean isWrapped(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    static String encrypt(String plain, String keyMaterial) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(keyMaterial), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(sealed, 0, stored, iv.length, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (Exception e) {
            throw new MailSettingException("The mail password could not be encrypted.");
        }
    }

    /**
     * Reads the password back. A value that was tampered with, or that was saved
     * under a different key, is refused rather than returned: sending a wrong
     * password to the university mail server can lock the account it belongs to,
     * so a broken value must never be tried.
     */
    static String decrypt(String stored, String keyMaterial) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!isWrapped(stored)) {
            throw new MailSettingException("The saved mail password is not readable; save it again.");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(keyMaterial), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MailSettingException(
                    "The saved mail password could not be decrypted; is LEAVE_MAIL_KEY the same as before?");
        }
    }

    private static SecretKeySpec key(String keyMaterial) throws Exception {
        byte[] material = MessageDigest.getInstance("SHA-256")
                .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(material, "AES");
    }
}
