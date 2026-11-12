package com.example.leaveplatform.mail;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.example.leaveplatform.notification.MessageStored;

/**
 * The step that turns a bell message into a mail.
 *
 * <p>It listens after commit on purpose. Everything the platform knows about a
 * leave decision is already saved when this runs, so a mail server that is
 * switched off, mis-typed or refusing connections can only ever make the log say
 * "not sent" - it cannot roll back an approval, slow a supervisor down, or mail
 * news about a request that was undone.
 *
 * <p>{@code fallbackExecution} keeps the behaviour honest outside a transaction:
 * a message written by a script or a maintenance command is still mailed rather
 * than silently dropped.
 */
@Component
class MessageStoredMailer {

    private final MailService mail;

    MessageStoredMailer(MailService mail) {
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMessageStored(MessageStored message) {
        mail.onMessageStored(message);
    }
}
