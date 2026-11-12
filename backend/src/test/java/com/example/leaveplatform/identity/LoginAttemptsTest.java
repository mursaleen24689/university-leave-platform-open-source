package com.example.leaveplatform.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import com.example.leaveplatform.audit.AuditService;

class LoginAttemptsTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final LoginAttempts attempts = new LoginAttempts(users, audit);

    @Test
    void countsAWrongPassword() {
        AppUser user = mock(AppUser.class);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        attempts.onFailure(new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken("z.ahmed", "wrong"),
                new BadCredentialsException("bad credentials")));

        verify(user).registerFailedAttempt(LoginAttempts.MAX_FAILURES, LoginAttempts.LOCK_DURATION);
    }

    @Test
    void ignoresDisabledAccounts() {
        AppUser user = mock(AppUser.class);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        attempts.onFailure(new AuthenticationFailureDisabledEvent(
                new UsernamePasswordAuthenticationToken("z.ahmed", "secret"),
                new DisabledException("disabled")));

        verify(user, never()).registerFailedAttempt(LoginAttempts.MAX_FAILURES, LoginAttempts.LOCK_DURATION);
    }

    @Test
    void ignoresUnknownUsernamesWithoutFailing() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        attempts.onFailure(new AuthenticationFailureBadCredentialsEvent(
                new UsernamePasswordAuthenticationToken("ghost", "wrong"),
                new BadCredentialsException("bad credentials")));

        verify(users, never()).save(any());
    }

    @Test
    void resetsFailuresAfterASuccessfulLogin() {
        AppUser user = mock(AppUser.class);
        when(users.findByUsername("z.ahmed")).thenReturn(Optional.of(user));

        attempts.onSuccess(new AuthenticationSuccessEvent(new UsernamePasswordAuthenticationToken(
                "z.ahmed", "secret", List.of())));

        verify(user).registerSuccessfulLogin();
    }
}
