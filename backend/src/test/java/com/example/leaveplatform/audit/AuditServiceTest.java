package com.example.leaveplatform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class AuditServiceTest {

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final AuditService audit = new AuditService(events);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsTheSignedInUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("z.ahmed", "ignored"));

        audit.record(AuditAction.ACCOUNT_CREATED, "ACCOUNT", 42L, "Created account.");

        AuditEvent saved = capture();
        assertThat(saved.getActorUsername()).isEqualTo("z.ahmed");
        assertThat(saved.getAction()).isEqualTo("ACCOUNT_CREATED");
        assertThat(saved.getTargetType()).isEqualTo("ACCOUNT");
        assertThat(saved.getTargetId()).isEqualTo("42");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void fallsBackToSystemWithoutASession() {
        audit.record(AuditAction.EMPLOYEE_IMPORTED, "EMPLOYEE", null, "Imported rows.");

        AuditEvent saved = capture();
        assertThat(saved.getActorUsername()).isEqualTo("system");
        assertThat(saved.getTargetId()).isNull();
    }

    @Test
    void treatsAnonymousAsSystem() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        audit.record(AuditAction.SIGNED_IN, "ACCOUNT", 1L, "Signed in.");

        assertThat(capture().getActorUsername()).isEqualTo("system");
    }

    @Test
    void anExplicitActorWins() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone.else", "ignored"));

        audit.recordAs("z.ahmed", AuditAction.PASSWORD_RESET, "ACCOUNT", 7L, "Reset.");

        assertThat(capture().getActorUsername()).isEqualTo("z.ahmed");
    }

    @Test
    void clipsLongDetailToTheColumnLength() {
        audit.recordAs("z.ahmed", AuditAction.EMPLOYEE_UPDATED, "EMPLOYEE", 1L, "x".repeat(1400));

        assertThat(capture().getDetail()).hasSize(1000);
    }

    @Test
    void recentWithoutATermListsTheLatestEntries() {
        when(events.findInWindow(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(new AuditEvent("admin", "SIGNED_IN", "ACCOUNT", "1", "Signed in.")));

        List<AuditEntry> entries = audit.recent("  ", null, null);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorUsername()).isEqualTo("admin");
        verify(events).findInWindow(any(), any(), any(Pageable.class));
    }

    @Test
    void aBackwardsWindowIsRefusedInsteadOfReturningNothing() {
        assertThatThrownBy(() -> audit.recent("payroll",
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(events, never()).search(any(), any(), any(), any(Pageable.class));
    }

    /**
     * The trail keeps every entry ever written, so a read that asks for all of
     * them would hand over the whole table. The cap is what the screen warns about.
     */
    @Test
    void theReadIsLimitedToTheNewestEntries() {
        when(events.findInWindow(any(), any(), any(Pageable.class))).thenReturn(List.of());

        audit.recent(null, null, null);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findInWindow(any(), any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(AuditService.LIST_LIMIT);
    }

    @Test
    void recentWithATermSearchesInsideTheDaysAskedFor() {
        when(events.search(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        audit.recent("  payroll ", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3));

        ArgumentCaptor<Instant> firstInstant = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> lastInstant = ArgumentCaptor.forClass(Instant.class);
        verify(events).search(eq("payroll"), firstInstant.capture(), lastInstant.capture(),
                any(Pageable.class));
        var zone = ZoneId.systemDefault();
        assertThat(firstInstant.getValue())
                .isEqualTo(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant());
        // the last day chosen is worked through to its end, which is the next
        // morning - so an entry typed at 23:59 on 3 November is still in the list.
        assertThat(lastInstant.getValue())
                .isEqualTo(LocalDate.of(2026, 11, 4).atStartOfDay(zone).toInstant());
    }

    /**
     * A blank date is not sent to the database as a null, because Oracle will not
     * compare a date column against a bind whose type it has to guess: the widest
     * range the table can hold is sent instead.
     */
    @Test
    void aBlankDateWindowMeansEveryDay() {
        when(events.search(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        audit.recent("payroll", null, null);

        ArgumentCaptor<Instant> firstInstant = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> lastInstant = ArgumentCaptor.forClass(Instant.class);
        verify(events).search(eq("payroll"), firstInstant.capture(), lastInstant.capture(),
                any(Pageable.class));
        assertThat(firstInstant.getValue()).isBefore(Instant.parse("1901-01-01T00:00:00Z"));
        assertThat(lastInstant.getValue()).isAfter(Instant.parse("9998-01-01T00:00:00Z"));
    }

    @Test
    void entriesGetTheirTimestampFromTheEvent() {
        Instant when = Instant.parse("2026-10-30T08:15:00Z");
        AuditEntry entry = new AuditEntry(3L, when, "admin", "SIGNED_IN", "ACCOUNT", "1", "Signed in.");

        assertThat(entry.occurredAt()).isEqualTo(when);
        assertThat(entry.id()).isEqualTo(3L);
    }

    private AuditEvent capture() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }
}
