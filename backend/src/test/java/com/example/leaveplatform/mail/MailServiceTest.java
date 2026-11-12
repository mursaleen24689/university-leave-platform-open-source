package com.example.leaveplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import com.example.leaveplatform.identity.ContactLookup;
import com.example.leaveplatform.notification.MessageStored;
import com.example.leaveplatform.notification.NotificationKind;

/**
 * The decisions about whether a message leaves the building, and what the log
 * says when it does not.
 *
 * <p>The transport is mocked, so every case here is the part a future maintainer
 * will actually change - the switch, the kind list, the missing address - and
 * none of it needs a mail server. What matters is that a refusal is written
 * down: an administrator who turns mail on and sees nothing must be able to open
 * the log and read the reason.
 */
class MailServiceTest {

    private static final String ADDRESS = "nadia@nedu.edu.pk";

    private final MailSettingsService settings = mock(MailSettingsService.class);
    private final MailAttemptRepository attempts = mock(MailAttemptRepository.class);
    private final MailTransport transport = mock(MailTransport.class);
    private final ContactLookup contacts = mock(ContactLookup.class);

    private final MailService service = new MailService(settings, attempts, transport, contacts,
            new MailKey("a key"));

    @Test
    void aSwitchedOffSetupNeverOpensTheMailSocketButSaysWhy() {
        when(settings.current()).thenReturn(row(false, Set.of()));

        assertThat(service.send(ADDRESS, "A leave request is waiting", "Body"))
                .isEqualTo("Not sent: Mail sending is switched off.");
        verify(transport, never()).send(any(), any(), any(), any(), any());

        ArgumentCaptor<MailAttempt> logged = ArgumentCaptor.forClass(MailAttempt.class);
        verify(attempts).save(logged.capture());
        assertThat(logged.getValue().getOutcome()).isEqualTo(MailAttempt.SKIPPED);
        assertThat(logged.getValue().getRecipient()).isEqualTo(ADDRESS);
    }

    @Test
    void noServerSavedIsReportedRatherThanLeftBlank() {
        MailSettings missing = new MailSettings(null, null, "leave", "leave@nedu.edu.pk",
                true, true, Set.of());
        when(settings.current()).thenReturn(missing);

        assertThat(service.send(ADDRESS, "Subject", "Body")).isEqualTo("Not sent: No mail server is saved.");
    }

    @Test
    void aMessageOfAMailedKindGoesToTheAddressOnTheStaffRecord() {
        when(settings.current()).thenReturn(row(true, Set.of(NotificationKind.REQUEST_DECIDED.name())));
        when(contacts.find("hod.mech")).thenReturn(Optional.of(
                new ContactLookup.Contact("hod.mech", ADDRESS)));

        service.onMessageStored(new MessageStored("hod.mech", NotificationKind.REQUEST_DECIDED,
                "Your leave was approved", "Three days from 11 January."));

        verify(transport).send(any(), eq("leave@nedu.edu.pk"), eq(ADDRESS),
                eq("Your leave was approved"), eq("Three days from 11 January."));
        verify(attempts).save(any());
    }

    @Test
    void aKindTheAdministratorDidNotListIsNotMailedAndNotEvenLookedUp() {
        when(settings.current()).thenReturn(row(true, Set.of(NotificationKind.REQUEST_DECIDED.name())));

        service.onMessageStored(new MessageStored("hod.mech", NotificationKind.REQUEST_WAITING,
                "A request is waiting", "Body"));

        verify(contacts, never()).find(anyString());
        verify(transport, never()).send(any(), any(), any(), any(), any());
        verify(attempts, never()).save(any());
    }

    @Test
    void aPersonWithNoAddressIsSkippedWithTheReasonInTheLog() {
        when(settings.current()).thenReturn(row(true, Set.of()));
        when(contacts.find("mechanic.sharer")).thenReturn(Optional.of(
                new ContactLookup.Contact("mechanic.sharer", null)));

        service.onMessageStored(new MessageStored("mechanic.sharer", NotificationKind.REQUEST_DECIDED,
                "Your leave was approved", "Body"));

        ArgumentCaptor<MailAttempt> logged = ArgumentCaptor.forClass(MailAttempt.class);
        verify(attempts).save(logged.capture());
        assertThat(logged.getValue().getOutcome()).isEqualTo(MailAttempt.SKIPPED);
        assertThat(logged.getValue().getDetail()).contains("No e-mail address");
        verify(transport, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void aClosedAccountIsSkippedToo() {
        when(settings.current()).thenReturn(row(true, Set.of()));
        when(contacts.find("left.the.service")).thenReturn(Optional.empty());

        service.onMessageStored(new MessageStored("left.the.service", NotificationKind.REQUEST_DECIDED,
                "Your leave was approved", "Body"));

        ArgumentCaptor<MailAttempt> logged = ArgumentCaptor.forClass(MailAttempt.class);
        verify(attempts).save(logged.capture());
        assertThat(logged.getValue().getDetail()).contains("closed");
    }

    @Test
    void aRefusedLoginAtTheMailServerBecomesASentenceAndNotAFailedApproval() {
        when(settings.current()).thenReturn(passwordedRow(true));
        doThrow(new MailDeliveryException("535 Username or Password is invalid",
                new IllegalStateException())).when(transport)
                .send(any(), any(), any(), any(), any());

        String outcome = service.send(ADDRESS, "Your leave was approved", "Body");

        assertThat(outcome).isEqualTo("Failed: 535 Username or Password is invalid");
        ArgumentCaptor<MailAttempt> logged = ArgumentCaptor.forClass(MailAttempt.class);
        verify(attempts).save(logged.capture());
        assertThat(logged.getValue().getOutcome()).isEqualTo(MailAttempt.FAILED);
    }

    @Test
    void aCipherSavedUnderADifferentKeyIsNeverTriedAgainstTheServer() {
        MailSettings row = row(true, Set.of());
        row.setPasswordCipher(SecretCipher.encrypt("real password", "some other phrase"));
        when(settings.current()).thenReturn(row);

        assertThat(service.send(ADDRESS, "Subject", "Body"))
                .contains("could not be decrypted");
        verify(transport, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void onlyTheConfiguredKindIsAskedAbout() {
        when(settings.current()).thenReturn(row(true, Set.of(NotificationKind.REQUEST_WAITING.name())));

        assertThat(service.mails(NotificationKind.REQUEST_WAITING)).isTrue();
        assertThat(service.mails(NotificationKind.REQUEST_DECIDED)).isFalse();
    }

    @Test
    void theLogComesBackNewestFirstAndStopsAtTheScreenLimit() {
        service.recentAttempts();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(attempts).findAllByOrderByIdDesc(page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(MailService.LOG_LIMIT);
    }

    private static MailSettings row(boolean enabled, Set<String> kinds) {
        return new MailSettings("mail.nedu.edu.pk", 587, "leave", "leave@nedu.edu.pk",
                true, enabled, kinds);
    }

    private static MailSettings passwordedRow(boolean enabled) {
        MailSettings row = row(enabled, Set.of());
        row.setPasswordCipher(SecretCipher.encrypt("real password", "a key"));
        return row;
    }
}
