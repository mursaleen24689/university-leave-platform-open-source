package com.example.leaveplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordServiceTest {

    private static final String USERNAME = "z.ahmed";
    private static final String CURRENT = "old-password-2026";
    private static final String NEW = "new-password-2026";
    private static final long USER_ID = 7L;

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordHistoryRepository history = mock(PasswordHistoryRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final com.example.leaveplatform.audit.AuditService audit =
            mock(com.example.leaveplatform.audit.AuditService.class);
    private final PasswordService service =
            new PasswordService(users, history, encoder, new PasswordPolicy(), audit);

    @Test
    void rejectsAWrongCurrentPassword() {
        givenUser();

        assertThatThrownBy(() -> service.change(USERNAME, "not-the-password", NEW))
                .isInstanceOf(WrongPasswordException.class);
    }

    @Test
    void rejectsANewPasswordThatBreaksThePolicy() {
        givenUser();

        assertThatThrownBy(() -> service.change(USERNAME, CURRENT, "short"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void rejectsANewPasswordEqualToTheCurrentOne() {
        givenUser();

        assertThatThrownBy(() -> service.change(USERNAME, CURRENT, CURRENT))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("differ");
    }

    @Test
    void rejectsAPasswordThatWasUsedRecently() {
        AppUser user = givenUser();
        when(history.findByUserIdOrderByChangedAtDesc(USER_ID))
                .thenReturn(List.of(new PasswordHistory(USER_ID, encoder.encode(NEW))));

        assertThatThrownBy(() -> service.change(USERNAME, CURRENT, NEW))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("recently");
        verify(user, never()).changePassword(anyString());
    }

    @Test
    void storesTheOldHashAndAppliesTheNewPassword() {
        AppUser user = givenUser();
        when(history.findByUserIdOrderByChangedAtDesc(USER_ID)).thenReturn(List.of());

        service.change(USERNAME, CURRENT, NEW);

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(user).changePassword(newHash.capture());
        assertThat(encoder.matches(NEW, newHash.getValue())).isTrue();
        verify(history).save(any(PasswordHistory.class));
    }

    private AppUser givenUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getUsername()).thenReturn(USERNAME);
        when(user.getPasswordHash()).thenReturn(encoder.encode(CURRENT));
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        return user;
    }
}
