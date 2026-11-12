package com.example.leaveplatform.identity;

import java.time.Duration;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;

/**
 * Counts failed sign-ins and locks the account for a while after repeated
 * failures. What this listener counts is a wrong password: a disabled or
 * already locked account is not punished further. A wrong authenticator code
 * never becomes an authentication failure, because it is refused before the
 * password is looked at, so {@code auth.TwoStepService} adds it to the same
 * counter with the same two numbers.
 */
@Component
public class LoginAttempts {

    /**
     * The one limit for both halves of signing in. Public because
     * {@code auth.TwoStepService} counts a wrong authenticator code into the
     * same total: six digits typed five times in fifteen minutes is as far as
     * anybody gets, whichever half of the account they are working on.
     */
    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final AuditService audit;

    public LoginAttempts(AppUserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        if (!(event.getException() instanceof BadCredentialsException)) {
            return;
        }
        String username = event.getAuthentication().getName();
        users.findByUsername(username)
                .ifPresent(user -> {
                    user.registerFailedAttempt(MAX_FAILURES, LOCK_DURATION);
                    if (user.isLocked(Instant.now())) {
                        audit.recordAs(username, AuditAction.ACCOUNT_LOCKED, "ACCOUNT", user.getId(),
                                "Locked after " + MAX_FAILURES + " wrong passwords.");
                    }
                });
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        users.findByUsername(username)
                .ifPresent(user -> {
                    user.registerSuccessfulLogin();
                    audit.recordAs(username, AuditAction.SIGNED_IN, "ACCOUNT", user.getId(), "Signed in.");
                });
    }
}
