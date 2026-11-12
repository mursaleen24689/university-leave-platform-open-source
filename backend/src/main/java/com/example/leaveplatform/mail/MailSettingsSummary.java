package com.example.leaveplatform.mail;

import java.time.Instant;
import java.util.Set;

/**
 * The mail settings as the screen may see them.
 *
 * <p>{@code hasPassword} instead of a password, always. Nothing in this record
 * can be printed by a browser's developer tools in a form that helps an attacker,
 * and no endpoint that returns it can leak the SMTP credentials by accident.
 */
public record MailSettingsSummary(
        boolean enabled,
        String host,
        Integer port,
        String username,
        boolean hasPassword,
        boolean keyIsConfigured,
        String fromAddress,
        boolean useTls,
        Set<String> mailedEvents,
        String updatedBy,
        Instant updatedAt) {
}
