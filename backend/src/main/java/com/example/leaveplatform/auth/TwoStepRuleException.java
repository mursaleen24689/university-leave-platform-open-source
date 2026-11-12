package com.example.leaveplatform.auth;

/**
 * A two-step action the platform cannot take, said in words the person who
 * asked for it can act on: the key is missing, the seed was not confirmed in
 * time, the code they typed does not match the app they are holding.
 *
 * <p>Never used for a failed sign-in. An attempt from whoever is standing at
 * the login screen gets the same general refusal as a wrong password, because
 * saying which half went wrong would tell an attacker that the other half was
 * right.
 */
public class TwoStepRuleException extends RuntimeException {

    public TwoStepRuleException(String message) {
        super(message);
    }
}
