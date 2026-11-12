package com.example.leaveplatform.profile;

/**
 * A proposed correction that cannot be accepted or cannot go on. The message is
 * written for the person reading the screen, and
 * {@code config/ProfileExceptionHandler} turns it into a readable 400.
 */
public class ProfileRuleException extends RuntimeException {

    public ProfileRuleException(String message) {
        super(message);
    }
}
