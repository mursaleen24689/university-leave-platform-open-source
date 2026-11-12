package com.example.leaveplatform.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The secret text that {@link SecretCipher} turns into an AES key.
 *
 * <p>It comes from the environment and never from the database, so the file that
 * holds the saved SMTP password cannot also decrypt itself. Losing it means the
 * saved password has to be typed again - which is the correct failure: nobody
 * can read a password from a stolen database dump, and nobody can quietly
 * recover one from a stolen backup of the settings table.
 */
@Component
public class MailKey {

    private final String material;

    public MailKey(@Value("${app.mail.key:}") String material) {
        this.material = material == null ? "" : material.trim();
    }

    /** The raw key text, or empty when the platform has never been given one. */
    public String material() {
        return material;
    }

    public boolean isPresent() {
        return !material.isBlank();
    }
}
