package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;

class PasswordChangeGuardTest {

    private static final String USERNAME = "z.ahmed";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordChangeGuard guard = new PasswordChangeGuard(users);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void asksForAChangeWhileTheAccountHasATemporaryPassword() {
        AppUser user = mock(AppUser.class);
        when(user.isMustChangePassword()).thenReturn(true);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        assertThat(guard.requiresChange(USERNAME)).isTrue();
    }

    @Test
    void stopsAskingOnceThePasswordHasBeenChanged() {
        AppUser user = mock(AppUser.class);
        when(user.isMustChangePassword()).thenReturn(false);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        assertThat(guard.requiresChange(USERNAME)).isFalse();
    }

    @Test
    void ignoresAnAccountThatIsNoLongerInTheDatabase() {
        when(users.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThat(guard.requiresChange(USERNAME)).isFalse();
    }

    @Test
    void treatsAnAnonymousRequestAsSignedOut() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(PasswordChangeGuard.signedInUsername()).isNull();
    }

    @Test
    void readsTheNameOfTheSignedInAccount() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));

        assertThat(PasswordChangeGuard.signedInUsername()).isEqualTo(USERNAME);
    }

    @Test
    void ignoresAnAuthenticationThatWasNeverCompleted() {
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, "n/a");
        SecurityContextHolder.getContext().setAuthentication(token);

        assertThat(PasswordChangeGuard.signedInUsername()).isNull();
    }
}
