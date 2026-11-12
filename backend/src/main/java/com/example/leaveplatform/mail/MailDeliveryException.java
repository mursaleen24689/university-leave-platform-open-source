package com.example.leaveplatform.mail;

/** The mail server was reached or not, in one sentence the log can hold. */
class MailDeliveryException extends RuntimeException {

    MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
