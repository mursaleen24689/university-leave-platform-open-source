package com.example.leaveplatform.mail;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;

/**
 * The mail part of the administration screen. Only the central administrator may
 * call it; the rule is in {@code SecurityConfig}, which guards every address
 * under {@code /api/v1/admin}.
 *
 * <p>There is no endpoint that returns the saved password, in any form. The
 * settings reply says whether one is stored, and the only way to change it is to
 * type it again. A test send goes through the same log as a real one, so what
 * the administrator sees after pressing "Send a test" is the same sentence the
 * platform would have written for any other message.
 */
@RestController
@RequestMapping("/api/v1/admin/mail")
class MailController {

    /** An address to try the settings against, typed on the screen. */
    public record TestRequest(String address) {
    }

    /** What the platform did, in words the screen can show as they are. */
    public record TestResult(String outcome) {
    }

    /** One line of the send log, newest first. */
    public record AttemptLine(Instant attemptedAt, String recipient, String subject, String outcome,
            String detail) {
    }

    private final MailSettingsService settings;
    private final MailService mail;
    private final AuditService audit;

    MailController(MailSettingsService settings, MailService mail, AuditService audit) {
        this.settings = settings;
        this.mail = mail;
        this.audit = audit;
    }

    @GetMapping("/settings")
    MailSettingsSummary settings() {
        return settings.summary();
    }

    @PutMapping("/settings")
    MailSettingsSummary save(@RequestBody MailSettingsInput input, Principal who) {
        return settings.update(input, who == null ? "system" : who.getName());
    }

    @PostMapping("/test")
    TestResult test(@RequestBody TestRequest request, Principal who) {
        String address = request.address() == null ? "" : request.address().trim();
        if (address.isEmpty()) {
            return new TestResult("Type an address to send the test to.");
        }
        // The attempt log keeps the recipient in VARCHAR2(320). Refusing here
        // rather than after the send means an over-long address cannot produce a
        // mail that went out and a log row that failed to record it.
        if (address.length() > 320) {
            return new TestResult("That address is too long to send to.");
        }
        audit.record(AuditAction.MAIL_TEST_SENT, "MAIL_SETTINGS", MailSettings.ROW,
                "A test mail was asked for " + address + ".");
        return new TestResult(mail.send(address, "university leave platform test",
                "This is a test from the leave platform. If you can read it, the settings work."));
    }

    @GetMapping("/log")
    List<AttemptLine> log() {
        return mail.recentAttempts().stream().map(attempt -> new AttemptLine(attempt.getAttemptedAt(),
                attempt.getRecipient(), attempt.getSubject(), attempt.getOutcome(),
                attempt.getDetail())).toList();
    }
}
