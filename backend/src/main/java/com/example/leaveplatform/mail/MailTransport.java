package com.example.leaveplatform.mail;

import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * The only place in the platform that opens a mail socket.
 *
 * <p>Kept apart from {@code MailService} so the deciding - is mail on, is this
 * kind mailed, does this person have an address - can be tested without a mail
 * server, which is the part a future maintainer will want changed and the part
 * that is never worth mocking badly.
 */
@Component
class MailTransport {

    void send(SmtpConfig config, String from, String to, String subject, String body) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setUsername(config.username());
        sender.setPassword(config.password());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", config.username() == null || config.username().isBlank()
                ? "false" : "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (config.useTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        JavaMailSenderImpl chosen = sender;
        try {
            MimeMessage message = chosen.createMimeMessage();
            MimeMessageHelper text = new MimeMessageHelper(message, "utf-8");
            text.setFrom(from);
            text.setTo(to);
            text.setSubject(subject);
            text.setText(body, false);
            chosen.send(message);
        } catch (Exception e) {
            throw new MailDeliveryException(firstWords(e), e);
        }
    }

    /**
     * The mail server's own sentence, which is the useful part: a Java stack of
     * wrapper exceptions says nothing to a person trying to find out whether the
     * password, the port or the certificate is the problem.
     */
    private static String firstWords(Exception e) {
        Throwable at = e;
        while (at.getCause() != null && at.getMessage() == null) {
            at = at.getCause();
        }
        return at.getClass().getSimpleName() + ": " + at.getMessage();
    }
}
