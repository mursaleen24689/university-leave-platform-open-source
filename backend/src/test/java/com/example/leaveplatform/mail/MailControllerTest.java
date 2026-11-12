package com.example.leaveplatform.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.example.leaveplatform.audit.AuditService;

/**
 * The test-send button, and the one address it must refuse before anything is
 * sent: the attempt log stores the recipient in VARCHAR2(320), so a longer one
 * would go out and then fail to be recorded.
 */
class MailControllerTest {

    private final MailSettingsService settings = mock(MailSettingsService.class);
    private final MailService mail = mock(MailService.class);
    private final AuditService audit = mock(AuditService.class);
    private final MailController controller = new MailController(settings, mail, audit);

    @Test
    void anAddressLongerThanTheLogCanStoreIsRefusedBeforeTheMailGoesOut() {
        MailController.TestResult result =
                controller.test(new MailController.TestRequest("a".repeat(321)), null);

        assertThat(result.outcome()).contains("too long");
        verifyNoInteractions(mail);
        verifyNoInteractions(audit);
    }

    @Test
    void anAddressOfExactlyTheStoredLengthIsStillSent() {
        String address = "a".repeat(308) + "@example.edu";
        when(mail.send(eq(address), anyString(), anyString())).thenReturn("Sent");

        MailController.TestResult result = controller.test(new MailController.TestRequest(address), null);

        assertThat(address).hasSize(320);
        assertThat(result.outcome()).isEqualTo("Sent");
        verify(mail).send(eq(address), anyString(), anyString());
    }

    @Test
    void anEmptyBoxAsksForAnAddressRatherThanSending() {
        assertThat(controller.test(new MailController.TestRequest("   "), null).outcome())
                .contains("Type an address");
        assertThat(controller.test(new MailController.TestRequest(null), null).outcome())
                .contains("Type an address");
        verifyNoInteractions(mail);
    }

    @Test
    void theAddressIsTrimmedAndTheSendIsRecorded() {
        when(mail.send(eq("clerk@example.edu"), anyString(), anyString())).thenReturn("Sent");

        controller.test(new MailController.TestRequest("  clerk@example.edu  "), null);

        verify(mail).send(eq("clerk@example.edu"), anyString(), anyString());
        verify(audit).record(any(), anyString(), any(), anyString());
    }
}