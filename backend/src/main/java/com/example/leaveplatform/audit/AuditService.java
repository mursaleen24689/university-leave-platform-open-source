package com.example.leaveplatform.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Writes the audit trail. Every privileged action calls {@link #record} inside
 * the transaction that performs the change, so a rolled-back change leaves no
 * entry and a successful one cannot lose it. The actor is read from the
 * security context; sign-in events, which run before a session exists, pass the
 * username explicitly.
 */
@Service
public class AuditService {

    /** How many entries one read of the trail returns; the screen says when it stops. */
    public static final int LIST_LIMIT = 200;
    private static final int MAX_DETAIL = 1000;
    private static final String SYSTEM = "system";

    /** Stands in for "no date given" without sending a null the database must type. */
    private static final LocalDate ANY_DAY_FIRST = LocalDate.of(1900, 1, 1);
    private static final LocalDate ANY_DAY_LAST = LocalDate.of(9999, 12, 30);

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    public void record(String action, String targetType, Object targetId, String detail) {
        recordAs(currentActor(), action, targetType, targetId, detail);
    }

    public void recordAs(String actorUsername, String action, String targetType, Object targetId, String detail) {
        String actor = StringUtils.hasText(actorUsername) ? actorUsername.trim() : SYSTEM;
        events.save(new AuditEvent(actor, action, targetType,
                targetId == null ? null : String.valueOf(targetId), clip(detail)));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> recent(String term, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The last day of the period cannot be before the first day.");
        }
        Instant start = (from == null ? ANY_DAY_FIRST : from).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = (to == null ? ANY_DAY_LAST : to.plusDays(1)).atStartOfDay(ZoneId.systemDefault()).toInstant();
        PageRequest page = PageRequest.of(0, LIST_LIMIT);
        List<AuditEvent> found = StringUtils.hasText(term)
                ? events.search(term.trim(), start, end, page)
                : events.findInWindow(start, end, page);
        return found.stream().map(AuditEntry::of).toList();
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    private static String clip(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }
}
