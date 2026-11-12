package com.example.leaveplatform.mail;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.notification.NotificationKind;

/**
 * Reads and saves the one row that says how the platform sends mail.
 *
 * <p>Two decisions here are deliberate. A password is encrypted only when the
 * platform has been given a key, and saving other fields never overwrites a
 * stored password with a blank one - an administrator correcting a port number
 * must not silently lose the credentials. And every save is audited with the
 * names of the fields that changed, because "who pointed the university's leave
 * mail at an outside server" is a question the audit trail has to answer.
 */
@Service
public class MailSettingsService {

    private static final int MAX_HOST = 250;
    private static final int MAX_ADDRESS = 320;

    private final MailSettingsRepository settings;
    private final MailKey key;
    private final AuditService audit;

    public MailSettingsService(MailSettingsRepository settings, MailKey key, AuditService audit) {
        this.settings = settings;
        this.key = key;
        this.audit = audit;
    }

    /** The settings row, created blank if the migration has not been run yet. */
    @Transactional
    public MailSettings current() {
        return settings.findById(MailSettings.ROW).orElseGet(() -> settings.save(new MailSettings(
                null, null, null, null, true, false, Set.of())));
    }

    @Transactional(readOnly = true)
    public MailSettingsSummary summary() {
        MailSettings row = current();
        return new MailSettingsSummary(row.isEnabled(), row.getHost(), row.getPort(), row.getUsername(),
                row.getPasswordCipher() != null && !row.getPasswordCipher().isBlank(), key.isPresent(),
                row.getFromAddress(), row.isUseTls(), row.events(), row.getUpdatedBy(), row.getUpdatedAt());
    }

    /**
     * Saves what the administrator typed.
     *
     * <p>A field that is absent from the request is left exactly as it is; a
     * field sent empty is cleared. That distinction is the whole point: the earlier
     * screen used to copy every column, blanks included, so approving a change to
     * somebody's phone number wiped their email address. A caller that sends one
     * field must be able to do that safely.
     */
    @Transactional
    public MailSettingsSummary update(MailSettingsInput input, String actorUsername) {
        MailSettings row = current();
        Set<String> changed = new LinkedHashSet<>();

        String host = supplied(input.host(), row.getHost(), MAX_HOST, "server address");
        Integer port = input.port() == null ? row.getPort() : input.port();
        if (port != null && (port < 1 || port > 65535)) {
            throw new MailSettingException("The port must be between 1 and 65535.");
        }
        String account = supplied(input.username(), row.getUsername(), MAX_HOST, "sign-in name");
        String from = supplied(input.fromAddress(), row.getFromAddress(), MAX_ADDRESS, "sender address");
        if (from != null && !from.contains("@")) {
            throw new MailSettingException(
                    "The sender address must be a full address like leave@neduniversity.edu.pk.");
        }
        Set<String> events = input.mailedEvents() == null ? row.events() : knownEvents(input.mailedEvents());
        boolean tls = input.useTls() == null ? row.isUseTls() : input.useTls();
        boolean enabled = input.enabled() == null ? row.isEnabled() : input.enabled();
        if (enabled) {
            require(host != null, "Turn mail on only after saving a server address.");
            require(port != null, "Turn mail on only after saving a port.");
            require(from != null, "Turn mail on only after saving a sender address.");
        }

        if (!same(host, row.getHost())) {
            row.setHost(host);
            changed.add("server");
        }
        if (port != null && !port.equals(row.getPort())) {
            row.setPort(port);
            changed.add("port");
        }
        if (!same(account, row.getUsername())) {
            row.setUsername(account);
            changed.add("sign-in name");
        }
        if (!same(from, row.getFromAddress())) {
            row.setFromAddress(from);
            changed.add("sender address");
        }
        if (tls != row.isUseTls()) {
            row.setUseTls(tls);
            changed.add("encryption");
        }
        if (!events.equals(row.events())) {
            row.setMailedEvents(events);
            changed.add("kinds mailed");
        }
        if (enabled != row.isEnabled()) {
            row.setEnabled(enabled);
            changed.add(enabled ? "switched on" : "switched off");
        }

        if (input.asksToClearPassword()) {
            row.setPasswordCipher(null);
            changed.add("password removed");
        } else if (input.carriesPassword()) {
            require(key.isPresent(), "The platform cannot save a mail password until LEAVE_MAIL_KEY is set. "
                    + "Ask the person who runs the server to add it, then save again.");
            row.setPasswordCipher(SecretCipher.encrypt(input.password().trim(), key.material()));
            changed.add("password");
        }

        row.setUpdatedBy(actorUsername);
        row.setUpdatedAt(Instant.now());
        MailSettings saved = settings.save(row);
        audit.record(AuditAction.MAIL_SETTINGS_UPDATED, "MAIL_SETTINGS", MailSettings.ROW,
                changed.isEmpty() ? "No field changed."
                        : "Changed: " + String.join(", ", changed) + ".");
        return summaryOf(saved);
    }

    private MailSettingsSummary summaryOf(MailSettings row) {
        return new MailSettingsSummary(row.isEnabled(), row.getHost(), row.getPort(), row.getUsername(),
                row.getPasswordCipher() != null && !row.getPasswordCipher().isBlank(), key.isPresent(),
                row.getFromAddress(), row.isUseTls(), row.events(), row.getUpdatedBy(), row.getUpdatedAt());
    }

    /** Names the screen may offer; anything else is a client bug, not a typo to guess at. */
    private static Set<String> knownEvents(Set<String> offered) {
        if (offered == null) {
            return Set.of();
        }
        Set<String> valid = java.util.Arrays.stream(NotificationKind.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String name : offered) {
            if (!valid.contains(name)) {
                throw new MailSettingException("Unknown notification kind: " + name);
            }
        }
        return new LinkedHashSet<>(offered);
    }

    /**
     * The value a field should hold after this save. Absent means keep; present
     * but empty means clear; anything else is trimmed and measured.
     */
    private static String supplied(String offered, String saved, int maximum, String label) {
        if (offered == null) {
            return saved;
        }
        return trimmed(offered, maximum, label);
    }

    private static String trimmed(String text, int maximum, String label) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String clean = text.trim();
        if (clean.length() > maximum) {
            throw new MailSettingException("The " + label + " is longer than " + maximum + " characters.");
        }
        return clean;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new MailSettingException(message);
        }
    }
}
