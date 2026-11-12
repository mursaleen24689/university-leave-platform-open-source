package com.example.leaveplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    @Test
    void countsFailuresWithoutLockingBeforeTheLimit() {
        AppUser user = newUser();

        user.registerFailedAttempt(MAX_FAILURES, LOCK);
        user.registerFailedAttempt(MAX_FAILURES, LOCK);
        user.registerFailedAttempt(MAX_FAILURES, LOCK);
        user.registerFailedAttempt(MAX_FAILURES, LOCK);

        assertThat(user.getFailedAttempts()).isEqualTo(4);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void locksAtTheLimitAndResetsTheCounter() {
        AppUser user = newUser();

        for (int i = 0; i < MAX_FAILURES; i++) {
            user.registerFailedAttempt(MAX_FAILURES, LOCK);
        }

        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(java.time.Instant.now());
        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    void doesNotCountFailuresWhileLocked() {
        AppUser user = newUser();
        for (int i = 0; i < MAX_FAILURES; i++) {
            user.registerFailedAttempt(MAX_FAILURES, LOCK);
        }
        java.time.Instant firstLock = user.getLockedUntil();

        user.registerFailedAttempt(MAX_FAILURES, LOCK);
        user.registerFailedAttempt(MAX_FAILURES, LOCK);

        assertThat(user.getLockedUntil()).isEqualTo(firstLock);
    }

    @Test
    void successfulLoginClearsFailuresAndTheLock() {
        AppUser user = newUser();
        user.registerFailedAttempt(MAX_FAILURES, LOCK);
        for (int i = 0; i < MAX_FAILURES; i++) {
            user.registerFailedAttempt(MAX_FAILURES, LOCK);
        }

        user.registerSuccessfulLogin();

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    private AppUser newUser() {
        return new AppUser(mock(Employee.class), "z.ahmed", "$2a$10$hash", false);
    }
}
