package com.example.leaveplatform.mail;

/** A mail setting that cannot be kept, said in the words the screen shows. */
public class MailSettingException extends RuntimeException {

    public MailSettingException(String message) {
        super(message);
    }
}
