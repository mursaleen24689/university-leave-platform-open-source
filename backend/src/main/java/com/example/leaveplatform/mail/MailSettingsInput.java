package com.example.leaveplatform.mail;

import java.util.Set;

/**
 * What the administration screen sends when it saves the mail settings.
 *
 * <p>{@code password} is write-only. It arrives when the administrator types it,
 * is encrypted and stored, and is never read back out of the database by this or
 * any other DTO - the screen is told only whether a password is saved.
 *
 * <p>For every field, absent means "keep what is saved" and empty means "clear
 * it". Without that difference a caller that updates one field would wipe the
 * others, which is the exact mistake an earlier screen made when it copied blank
 * columns over a staff record.
 */
public record MailSettingsInput(
        Boolean enabled,
        String host,
        Integer port,
        String username,
        String password,
        String fromAddress,
        Boolean useTls,
        Set<String> mailedEvents,
        Boolean clearPassword) {

    /** Whether a new password is being supplied rather than a field correction. */
    public boolean carriesPassword() {
        return password != null && !password.isBlank();
    }

    public boolean asksToClearPassword() {
        return Boolean.TRUE.equals(clearPassword);
    }
}
