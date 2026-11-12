package com.example.leaveplatform.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One attempt to send one mail, kept so the person who typed the settings can see
 * whether the platform ever reached the mail server and what it answered.
 *
 * <p>The recipient is stored as it was given, and the detail is the mail server's
 * own words with a limit - never the password, never the message body. A record
 * that quoted the body would end up holding somebody's leave remarks in a table
 * that is not about leave at all.
 */
@Entity
@Table(name = "mail_log")
public class MailAttempt {

    static final int MAX_DETAIL = 1000;

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(length = 320)
    private String recipient;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false, updatable = false, length = 20)
    private String outcome;

    @Column(length = 1000)
    private String detail;

    protected MailAttempt() {
    }

    private MailAttempt(String recipient, String subject, String outcome, String detail) {
        this.recipient = recipient;
        this.subject = subject;
        this.outcome = outcome;
        this.detail = detail;
        this.attemptedAt = Instant.now();
    }

    static MailAttempt sent(String recipient, String subject) {
        return new MailAttempt(recipient, subject, SENT, null);
    }

    static MailAttempt failed(String recipient, String subject, String why) {
        return new MailAttempt(recipient, subject, FAILED, clip(why));
    }

    /**
     * Not an error: the settings said do not mail this kind, the account has no
     * address, or the account is closed. Keeping the row is what tells a person
     * that the message existed and was deliberately not sent, which is a
     * different answer from "I tried and failed".
     */
    static MailAttempt skipped(String recipient, String subject, String why) {
        return new MailAttempt(recipient, subject, SKIPPED, clip(why));
    }

    private static String clip(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= MAX_DETAIL ? oneLine : oneLine.substring(0, MAX_DETAIL);
    }

    public Long getId() {
        return id;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }
}
