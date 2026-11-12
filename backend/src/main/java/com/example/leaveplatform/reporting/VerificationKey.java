package com.example.leaveplatform.reporting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The secret text that turns a leave request's number into a verification code.
 *
 * <p>It comes from the environment and never from the database, so a stolen
 * dump cannot mint codes for its own rows - the same rule {@code mail/MailKey}
 * set for the saved SMTP password. Losing it means every code already printed
 * on a form stops checking and new forms print without one, which is the
 * correct failure: a paper form is worthless as proof either way, and nobody
 * can quietly carry on signing codes with a key the university no longer trusts.
 */
@Component
public class VerificationKey {

    private final String material;

    public VerificationKey(@Value("${app.verify.key:}") String material) {
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
