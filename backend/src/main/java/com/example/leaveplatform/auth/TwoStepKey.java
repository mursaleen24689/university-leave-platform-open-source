package com.example.leaveplatform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The secret text that {@link EnrolmentCipher} turns into an AES key, and the
 * only place two-step's key is read.
 *
 * <p>It comes from the environment and never from the database, so the table
 * that holds the sealed seeds cannot also open them. Losing it is recoverable
 * and it stays honest about that: the seeds in the database become unreadable,
 * every enrolled person has to enrol again, and nobody in between can sign in
 * as them by holding a dump.
 *
 * <p>Separate from {@code mail.MailKey} on purpose. One phrase covering both
 * would mean one leaked file unlocking the university's outgoing mail and every
 * account's second factor; two phrases mean two separate mistakes.
 */
@Component
class TwoStepKey {

    private final String material;

    TwoStepKey(@Value("${app.two-step.key:}") String material) {
        this.material = material == null ? "" : material.trim();
    }

    /** The raw key text, or empty when the platform has never been given one. */
    String material() {
        return material;
    }

    boolean isPresent() {
        return !material.isBlank();
    }
}
