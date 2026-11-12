package com.example.leaveplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.identity.LoginAttempts;

/**
 * The three paths through an authenticator setup - turning it on, signing in
 * with it, turning it off - and the rule that holds all three together: nothing
 * is stored until a code proves the phone really has the seed, and nothing is
 * given away to whoever is guessing.
 */
class TwoStepServiceTest {

    private static final String USERNAME = "ayesha.khan";
    private static final String PASSWORD = "Ayesha-Khan-2026";
    private static final String KEY = "a phrase only the server knows";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AppUser user = mock(AppUser.class);
    private final TwoStepService.PendingStore pending = new InBrowserStore();

    private final String hash = encoder.encode(PASSWORD);
    private TwoStepService service;

    @BeforeEach
    void anAccountWithItsOwnPassword() {
        service = serviceSealedWith(KEY);
        when(user.getUsername()).thenReturn(USERNAME);
        when(user.getPasswordHash()).thenReturn(hash);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.hasTwoStep()).thenReturn(false);
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    // ---------------------------------------------------------------- turning on

    @Test
    void refusesToOfferASeedUntilThePlatformHasAKeyOfItsOwn() {
        service = serviceSealedWith("");

        assertThatThrownBy(() -> service.beginEnrolment(USERNAME, PASSWORD, pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("LEAVE_TWOSTEP_KEY");
    }

    @Test
    void refusesToSetUpATemporaryPasswordAccount() {
        when(user.isMustChangePassword()).thenReturn(true);

        assertThatThrownBy(() -> service.beginEnrolment(USERNAME, PASSWORD, pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("Choose your own password first");
    }

    @Test
    void refusesToStartASecondSetupOnAnAccountAlreadyUsingOne() {
        givenTwoStepIsOn(SEED);

        assertThatThrownBy(() -> service.beginEnrolment(USERNAME, PASSWORD, pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("already on");
    }

    @Test
    void wantsTheRealPasswordBeforeItShowsAnybodyASeed() {
        assertThatThrownBy(() -> service.beginEnrolment(USERNAME, "Wrong-Password-2026", pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("not your current password");
        assertThatThrownBy(() -> service.beginEnrolment(USERNAME, null, pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("Type your current password");

        assertThat(pending.get(TwoStepService.PENDING_SEED)).isNull();
    }

    @Test
    void handsTheScreenEverythingAnAppNeedsAndWritesNothingYet() {
        TwoStepService.Enrolment enrolment = service.beginEnrolment(USERNAME, PASSWORD, pending);

        assertThat(enrolment.seed()).hasSize(32).matches("[A-Z2-7]{32}");
        assertThat(enrolment.seedForTyping()).isEqualTo(OneTimeCodes.grouped(enrolment.seed()));
        assertThat(enrolment.otpAuthUri()).startsWith("otpauth://totp/").contains(enrolment.seed());
        assertThat(enrolment.qrSvg()).startsWith("<svg").contains("aria-label");
        assertThat(enrolment.expiresAt()).isAfter(Instant.now());

        // The seed is in this browser's session, which is where a setup in
        // progress belongs: the account itself still has nothing.
        assertThat(pending.get(TwoStepService.PENDING_SEED)).isEqualTo(enrolment.seed());
        verify(user, never()).enrolTwoStep(any(), any());
    }

    @Test
    void aCodeFromTheAppIsWhatPutsTheSeedInTheAccount() {
        TwoStepService.Enrolment enrolment = service.beginEnrolment(USERNAME, PASSWORD, pending);
        String code = codeOf(enrolment.seed());

        TwoStepService.Status status = service.confirmEnrolment(USERNAME, code, pending);

        ArgumentCaptor<String> sealed = ArgumentCaptor.forClass(String.class);
        verify(user).enrolTwoStep(sealed.capture(), any(Instant.class));
        assertThat(sealed.getValue()).startsWith("v1:").doesNotContain(enrolment.seed());
        assertThat(EnrolmentCipher.open(sealed.getValue(), KEY)).isEqualTo(enrolment.seed());
        assertThat(status.enabled()).isTrue();
        assertThat(status.since()).isNotNull();
        verify(audit).recordAs(eq(USERNAME), eq(AuditAction.TWO_STEP_ENABLED), eq("ACCOUNT"), any(), any());
        assertThat(pending.get(TwoStepService.PENDING_SEED)).isNull();
    }

    @Test
    void anAccountThePersonWalkedAwayFromIsLeftAsItWas() {
        service.beginEnrolment(USERNAME, PASSWORD, pending);

        // A mistyped code is corrected by the person on their own phone; the
        // database never sees a seed that nothing confirmed.
        assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, "000000", pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("not the code your app is showing");

        verify(user, never()).enrolTwoStep(any(), any());
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());
    }

    @Test
    void threeMistypedCodesThrowTheSetupAway() {
        service.beginEnrolment(USERNAME, PASSWORD, pending);

        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, "000000", pending))
                    .isInstanceOf(TwoStepRuleException.class)
                    .hasMessageContaining("wait for the number to change");
        }
        assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, "000000", pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("three times, so this setup has been thrown away");

        assertThat(pending.get(TwoStepService.PENDING_SEED)).isNull();
        assertThat(pending.get(TwoStepService.PENDING_WRONG_CODES)).isNull();
        // The seed is gone, so even typing the right number now cannot enrol a
        // setup the person has stopped watching.
        assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, "000000", pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("Nothing is waiting to be confirmed");
    }

    @Test
    void aSetupThatWasLeftOpenLapses() {
        service.beginEnrolment(USERNAME, PASSWORD, pending);
        pending.put(TwoStepService.PENDING_EXPIRES_AT, Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, codeOf(SEED), pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("timed out");
        assertThat(pending.get(TwoStepService.PENDING_SEED)).isNull();
    }

    @Test
    void refusesToConfirmASetupThatWasNeverStarted() {
        assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, "123456", pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("Nothing is waiting to be confirmed");
    }

    @Test
    void refusesToConfirmTwice() {
        String code = enrolNow();
        assertThatThrownBy(() -> service.confirmEnrolment(USERNAME, code, pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("already on");
    }

    @Test
    void anUnknownAccountIsNotAScreenToShow() {
        when(users.findByUsername("ghost.khan")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.beginEnrolment("ghost.khan", PASSWORD, pending))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("signed-in account of its own");
    }

    // ---------------------------------------------------------------- signing in

    @Test
    void anAccountWithoutASecondStepIsNobodyBusinessButItsOwners() {
        assertThat(service.checkSignInCode(USERNAME, null)).isEqualTo(TwoStepService.CodeResult.NOT_ENROLLED);
        assertThat(service.checkSignInCode(null, "123456")).isEqualTo(TwoStepService.CodeResult.NOT_ENROLLED);
        assertThat(service.checkSignInCode("  ", "123456")).isEqualTo(TwoStepService.CodeResult.NOT_ENROLLED);
        when(users.findByUsername("ghost.khan")).thenReturn(Optional.empty());
        assertThat(service.checkSignInCode("ghost.khan", "123456"))
                .isEqualTo(TwoStepService.CodeResult.NOT_ENROLLED);

        verify(user, never()).registerFailedAttempt(anyInt(), any());
    }

    @Test
    void agreesWhenTheAppAndThePlatformDoTheSameSums() {
        String code = enrolNow();

        assertThat(service.checkSignInCode(USERNAME, code)).isEqualTo(TwoStepService.CodeResult.ACCEPTED);
        // A paste with the spaces a person puts in while reading it off a phone
        // is the same number as the one without them.
        assertThat(service.checkSignInCode(USERNAME, " " + code + " "))
                .isEqualTo(TwoStepService.CodeResult.ACCEPTED);
        verify(user, never()).registerFailedAttempt(anyInt(), any());
    }

    @Test
    void aMissingOrWrongCodeIsRefusedAndCountedWithThePasswords() {
        enrolNow();

        assertThat(service.checkSignInCode(USERNAME, null)).isEqualTo(TwoStepService.CodeResult.REFUSED);
        assertThat(service.checkSignInCode(USERNAME, "000000")).isEqualTo(TwoStepService.CodeResult.REFUSED);

        // The same five and the same fifteen minutes that a wrong password
        // costs, so an enrolled account is not two separate budgets to spend.
        verify(user, times(2)).registerFailedAttempt(
                LoginAttempts.MAX_FAILURES, LoginAttempts.LOCK_DURATION);
        assertThat(LoginAttempts.MAX_FAILURES).isEqualTo(5);
        assertThat(LoginAttempts.LOCK_DURATION).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void aSeedThePlatformCannotOpenSignsNobodyIn() {
        when(user.hasTwoStep()).thenReturn(true);
        when(user.getTwoFactorSecret()).thenReturn("a seed somebody typed by hand");

        assertThat(service.checkSignInCode(USERNAME, "123456"))
                .isEqualTo(TwoStepService.CodeResult.SEED_UNREADABLE);

        // Not counted as the person's failure: the recovery is the
        // administration team turning two-step off, and five guesses of theirs
        // would only bury the log line that tells them what to do.
        verify(user, never()).registerFailedAttempt(anyInt(), any());
    }

    // ---------------------------------------------------------------- turning off

    @Test
    void turningOffNeedsBothHalvesOfWhatItTakesToTurnOn() {
        String code = enrolNow();

        assertThatThrownBy(() -> service.turnOff(USERNAME, "Wrong-Password-2026", code))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("not your current password");
        assertThatThrownBy(() -> service.turnOff(USERNAME, PASSWORD, null))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("Type the code your app is showing");
        assertThatThrownBy(() -> service.turnOff(USERNAME, PASSWORD, "000000"))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("not the code your app is showing");

        verify(user, never()).clearTwoStep();
        verify(audit, never()).recordAs(any(), eq(AuditAction.TWO_STEP_DISABLED), any(), any(), any());
    }

    @Test
    void theOwnerWithTheirPasswordAndTheirPhoneCanHaveTheirPasswordAloneBack() {
        String code = enrolNow();

        service.turnOff(USERNAME, PASSWORD, code);

        verify(user).clearTwoStep();
        verify(audit).recordAs(eq(USERNAME), eq(AuditAction.TWO_STEP_DISABLED), eq("ACCOUNT"), any(), any());
    }

    @Test
    void turningOffAnAccountThatNeverUsedItSaysSo() {
        assertThatThrownBy(() -> service.turnOff(USERNAME, PASSWORD, "123456"))
                .isInstanceOf(TwoStepRuleException.class)
                .hasMessageContaining("not on for your account");
    }

    // ---------------------------------------------------------------- what it says

    @Test
    void reportsWhatTheAccountAlreadyHas() {
        Instant since = Instant.parse("2026-11-09T05:30:00Z");
        givenTwoStepIsOn(SEED);
        when(user.getTwoFactorEnrolledAt()).thenReturn(since);

        assertThat(service.status(USERNAME)).isEqualTo(new TwoStepService.Status(true, since));
        when(users.findByUsername("ghost.khan")).thenReturn(Optional.empty());
        assertThat(service.status("ghost.khan")).isEqualTo(TwoStepService.Status.OFF);
    }

    @Test
    void tellsTheAppWhoseAccountItIsAndHowTheNumberIsWorkedOut() {
        String uri = service.otpAuthUri(USERNAME, SEED);

        assertThat(uri).isEqualTo("otpauth://totp/University%20Leave%3Aayesha.khan"
                + "?secret=" + SEED
                + "&issuer=University%20Leave"
                + "&algorithm=SHA1&digits=6&period=30");
        // A space is %20 and never a plus: apps disagree about pluses, and a
        // mis-read issuer is a list of accounts nobody can tell apart.
        assertThat(uri).doesNotContain("+");
    }

    @Test
    void encodesAnIssuerAndAnAccountThatHoldOddCharacters() {
        TwoStepService spaced = new TwoStepService(users, encoder, new TwoStepKey(KEY), audit,
                "Leave Platform + Help Desk", 6, 30, 1, 15);

        assertThat(spaced.otpAuthUri("a k", SEED))
                .startsWith("otpauth://totp/Leave%20Platform%20%2B%20Help%20Desk%3Aa%20k?")
                .contains("&issuer=Leave%20Platform%20%2B%20Help%20Desk");
    }

    // ---------------------------------------------------------------- helpers

    /** The seed from RFC 6238's own table, so a broken cipher cannot hide behind a random one. */
    private static final String SEED = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private TwoStepService serviceSealedWith(String keyMaterial) {
        return new TwoStepService(users, encoder, new TwoStepKey(keyMaterial), audit,
                "University Leave", 6, 30, 1, 15);
    }

    private void givenTwoStepIsOn(String seed) {
        when(user.hasTwoStep()).thenReturn(true);
        when(user.getTwoFactorSecret()).thenReturn(EnrolmentCipher.seal(seed, KEY));
    }

    /** Turns two-step on the way the screen does, and hands back a live code. */
    private String enrolNow() {
        TwoStepService.Enrolment enrolment = service.beginEnrolment(USERNAME, PASSWORD, pending);
        String code = codeOf(enrolment.seed());
        service.confirmEnrolment(USERNAME, code, pending);
        givenTwoStepIsOn(enrolment.seed());
        return code;
    }

    private static String codeOf(String seed) {
        return OneTimeCodes.at(seed, Instant.now(), 30, 6);
    }

    /** Stands in for the browser's own session, which is where a setup in progress lives. */
    private static final class InBrowserStore implements TwoStepService.PendingStore {

        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, Object value) {
            values.put(key, value);
        }

        @Override
        public void drop(String key) {
            values.remove(key);
        }
    }
}
