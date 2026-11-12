package com.example.leaveplatform.mail;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.leaveplatform.identity.ContactLookup;
import com.example.leaveplatform.notification.MessageStored;
import com.example.leaveplatform.notification.NotificationKind;

/**
 * Sends one mail and records the attempt, whatever the answer was.
 *
 * <p>Every reason not to send is written down rather than left silent. A
 * university that switches mail on and sees nothing happen needs the log to say
 * "this account has no address" or "this kind is not mailed" - otherwise the
 * honest conclusion is that the feature is broken.
 *
 * <p>The settings are read again for every message, so a host typed at four
 * o'clock is used at four oh-one without a restart. A failure is never thrown to
 * the caller: the in-app message was already committed before this ran, and a
 * mail server that is down must not look like a leave request that failed.
 */
@Service
public class MailService {

    /** How many attempts the administration screen is shown. */
    public static final int LOG_LIMIT = 50;

    private final MailSettingsService settings;
    private final MailAttemptRepository attempts;
    private final MailTransport transport;
    private final ContactLookup contacts;
    private final String keyMaterial;

    public MailService(MailSettingsService settings, MailAttemptRepository attempts,
            MailTransport transport, ContactLookup contacts, MailKey key) {
        this.settings = settings;
        this.attempts = attempts;
        this.transport = transport;
        this.contacts = contacts;
        this.keyMaterial = key.material();
    }

    /**
     * Mails the bell message that was just stored, when the kind is one the
     * administrator asked to have mailed and the person has an address. Called
     * after the notification is committed, so nothing here can undo a decision.
     */
    @Transactional
    public void onMessageStored(MessageStored message) {
        MailSettings row = settings.current();
        if (!row.mails(message.kind().name())) {
            return;
        }
        Optional<ContactLookup.Contact> person = contacts.find(message.recipientUsername());
        if (person.isEmpty()) {
            note(MailAttempt.skipped(message.recipientUsername(), message.title(),
                    "That account is closed or no longer exists."));
            return;
        }
        ContactLookup.Contact contact = person.get();
        if (!contact.reachable()) {
            note(MailAttempt.skipped(contact.username(), message.title(),
                    "No e-mail address is saved for that person."));
            return;
        }
        send(contact.address(), message.title(), message.body());
    }

    /**
     * Sends to an address that is already known to be the right one, and returns
     * the sentence the screen should show: sent, or what stopped it.
     */
    @Transactional
    public String send(String address, String subject, String body) {
        MailSettings row = settings.current();
        if (!row.isEnabled()) {
            return note(MailAttempt.skipped(address, subject, "Mail sending is switched off."));
        }
        if (isBlank(row.getHost()) || row.getPort() == null) {
            return note(MailAttempt.skipped(address, subject, "No mail server is saved."));
        }
        if (isBlank(row.getFromAddress())) {
            return note(MailAttempt.skipped(address, subject, "No sender address is saved."));
        }
        String password;
        try {
            password = SecretCipher.decrypt(row.getPasswordCipher(), keyMaterial);
        } catch (MailSettingException e) {
            return note(MailAttempt.failed(address, subject, e.getMessage()));
        }
        SmtpConfig config = new SmtpConfig(row.getHost().trim(), row.getPort(),
                row.getUsername(), password, row.isUseTls());
        try {
            transport.send(config, row.getFromAddress().trim(), address, subject, body);
            return note(MailAttempt.sent(address, subject));
        } catch (MailDeliveryException e) {
            return note(MailAttempt.failed(address, subject, e.getMessage()));
        }
    }

    /** Whether a message of this kind should go out as mail at all. */
    @Transactional(readOnly = true)
    public boolean mails(NotificationKind kind) {
        return settings.current().mails(kind.name());
    }

    @Transactional(readOnly = true)
    public List<MailAttempt> recentAttempts() {
        return attempts.findAllByOrderByIdDesc(PageRequest.of(0, LOG_LIMIT));
    }

    /**
     * The sentence the screen shows. A skipped message is a decision the
     * settings made - switched off, wrong kind, no address - and says so; a
     * failure is the mail server refusing, whose own words are the useful part.
     */
    private String note(MailAttempt attempt) {
        attempts.save(attempt);
        return switch (attempt.getOutcome()) {
            case MailAttempt.SENT -> "Sent.";
            case MailAttempt.SKIPPED -> "Not sent: " + attempt.getDetail();
            default -> "Failed: " + attempt.getDetail();
        };
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
