package com.example.leaveplatform.leave;

/**
 * A request that is well formed but breaks a leave rule. The message is meant
 * for the person who made the request.
 */
public class LeaveRuleException extends RuntimeException {

    public LeaveRuleException(String message) {
        super(message);
    }
}
