package com.example.leaveplatform.delegation;

/**
 * A hand-over of approving that cannot be accepted, cannot be written, or cannot
 * be acted under. The message is written for the person reading the screen, and
 * {@code config/DelegationExceptionHandler} turns it into a readable 400.
 */
public class DelegationRuleException extends RuntimeException {

    public DelegationRuleException(String message) {
        super(message);
    }
}
