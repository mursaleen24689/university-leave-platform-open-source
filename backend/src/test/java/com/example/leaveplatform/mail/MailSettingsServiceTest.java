package com.example.leaveplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.notification.NotificationKind;

/**
 * What the administrator is allowed to save, and what the saved row says back.
 *
 * <p>The interesting cases are the ones that could quietly break a working
 * setup: a blank password field must not erase the credentials, a port of zero
 * must not be accepted, and switching mail on before the server is known must
 * be refused rather than left to fail on the first message.
 */
class MailSettingsServiceTest {

    private static final String ACTOR = "admin";

    private final MailSettingsRepository repository = mock(MailSettingsRepository.class);
    private final AuditService audit = mock(AuditService.class);

    @Test
    void aBlankRowStartsSwitchedOffWithNothingFilledIn() {
        MailSettingsService service = service("a key");
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        MailSettingsSummary summary = service.summary();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.host()).isNull();
        assertThat(summary.hasPassword()).isFalse();
        assertThat(summary.mailedEvents()).isEmpty();
    }

    @Test
    void aPasswordIsEncryptedAndNeverComesBackOutOfTheSummary() {
        MailSettingsService service = service("a key");
        MailSettings row = filled();
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        MailSettingsSummary summary = service.update(input("postage", Set.of()), ACTOR);

        assertThat(row.getPasswordCipher()).startsWith("v1:").doesNotContain("postage");
        assertThat(summary.hasPassword()).isTrue();
        assertThat(summary.toString()).doesNotContain("postage");
        verify(audit).record(eq(AuditAction.MAIL_SETTINGS_UPDATED), eq("MAIL_SETTINGS"),
                eq(MailSettings.ROW), anyString());
    }

    @Test
    void leavingThePasswordFieldBlankKeepsThePasswordThatIsAlreadySaved() {
        MailSettingsService service = service("a key");
        MailSettings row = filled();
        row.setPasswordCipher(SecretCipher.encrypt("kept", "a key"));
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        MailSettingsSummary summary = service.update(input("   ", Set.of()), ACTOR);

        assertThat(summary.hasPassword()).isTrue();
        assertThat(SecretCipher.decrypt(row.getPasswordCipher(), "a key")).isEqualTo("kept");
    }

    @Test
    void withoutAKeyThePlatformSavesEverythingExceptAPassword() {
        MailSettingsService service = service("");
        MailSettings row = filled();
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.update(input("would be plain text", Set.of()), ACTOR))
                .isInstanceOf(MailSettingException.class)
                .hasMessageContaining("LEAVE_MAIL_KEY");
        assertThat(row.getHost()).isEqualTo("mail.example.edu");
    }

    @Test
    void mailIsRefusedOnUntilTheServerAndSenderAreKnown() {
        MailSettingsService service = service("a key");
        MailSettings row = new MailSettings(null, null, null, null, true, false, Set.of());
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.update(new MailSettingsInput(true, null, null, null, null,
                null, true, Set.of(), false), ACTOR))
                .isInstanceOf(MailSettingException.class)
                .hasMessageContaining("server address");
    }

    @Test
    void aPortOutsideTheRangeAndAnIncompleteSenderAreBothRefused() {
        MailSettingsService service = service("a key");
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(filled()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.update(new MailSettingsInput(false, "h", 70000, null, null,
                null, true, Set.of(), false), ACTOR))
                .isInstanceOf(MailSettingException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> service.update(new MailSettingsInput(false, "h", 25, null, null,
                "no-at-here", true, Set.of(), false), ACTOR))
                .isInstanceOf(MailSettingException.class)
                .hasMessageContaining("sender address");
    }

    /**
     * The rule that stops a one-field save from wrecking the rest of the
     * settings: a field the request does not mention is left alone, and only an
     * empty string clears one.
     */
    @Test
    void aRequestThatNamesOnlyThePortLeavesEveryOtherFieldAlone() {
        MailSettingsService service = service("a key");
        MailSettings row = filled();
        row.setPasswordCipher(SecretCipher.encrypt("kept", "a key"));
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        MailSettingsSummary summary = service.update(
                new MailSettingsInput(null, null, 25, null, null, null, null, null, null), ACTOR);

        assertThat(row.getHost()).isEqualTo("mail.example.edu");
        assertThat(row.getFromAddress()).isEqualTo("leave@example.edu");
        assertThat(row.getPort()).isEqualTo(25);
        assertThat(summary.hasPassword()).isTrue();
    }

    @Test
    void anEmptySenderAddressClearsItWhereAnAbsentOneWouldNot() {
        MailSettingsService service = service("a key");
        MailSettings row = filled();
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(new MailSettingsInput(false, "mail.example.edu", 587, "leave", null,
                "", true, null, false), ACTOR);

        ArgumentCaptor<MailSettings> saved = ArgumentCaptor.forClass(MailSettings.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFromAddress()).isNull();
        assertThat(saved.getValue().getHost()).isEqualTo("mail.example.edu");
    }

    @Test
    void onlyKnownNotificationKindsCanBeMailed() {
        MailSettingsService service = service("a key");
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(filled()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.update(input(null, Set.of("NOT_A_THING")), ACTOR))
                .isInstanceOf(MailSettingException.class)
                .hasMessageContaining("Unknown notification kind");
    }

    @Test
    void anEmptyKindListMeansEveryKindTheBellShows() {
        MailSettings row = new MailSettings("mail.example.edu", 587, "leave", "leave@example.edu",
                true, true, Set.of());

        assertThat(row.mails(NotificationKind.REQUEST_DECIDED.name())).isTrue();
        assertThat(row.mails(NotificationKind.REQUEST_WAITING.name())).isTrue();
    }

    @Test
    void aListedKindIsMailedAndAnUnlistedOneIsNot() {
        MailSettings row = new MailSettings("mail.example.edu", 587, "leave", "leave@example.edu",
                true, true, Set.of(NotificationKind.REQUEST_DECIDED.name()));

        assertThat(row.mails(NotificationKind.REQUEST_DECIDED.name())).isTrue();
        assertThat(row.mails(NotificationKind.REQUEST_WAITING.name())).isFalse();
    }

    @Test
    void aDisabledRowMailsNothingWhateverItLists() {
        MailSettings row = new MailSettings("mail.example.edu", 587, "leave", "leave@example.edu",
                true, false, Set.of());

        assertThat(row.mails(NotificationKind.REQUEST_DECIDED.name())).isFalse();
    }

    @Test
    void theAuditLineNamesOnlyWhatActuallyChanged() {
        MailSettingsService service = service("a key");
        MailSettings row = filled();
        when(repository.findById(MailSettings.ROW)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(new MailSettingsInput(false, "mail.example.edu", 25, null, null,
                "leave@example.edu", true, Set.of(), false), ACTOR);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.MAIL_SETTINGS_UPDATED), eq("MAIL_SETTINGS"),
                eq(MailSettings.ROW), detail.capture());
        assertThat(detail.getValue()).isEqualTo("Changed: port.");
    }

    private MailSettingsService service(String keyMaterial) {
        return new MailSettingsService(repository, new MailKey(keyMaterial), audit);
    }

    private static MailSettings filled() {
        return new MailSettings("mail.example.edu", 587, "leave", "leave@example.edu",
                true, false, Set.of());
    }

    private static MailSettingsInput input(String password, Set<String> kinds) {
        return new MailSettingsInput(false, "mail.example.edu", 587, "leave", password,
                "leave@example.edu", true, kinds, false);
    }
}
