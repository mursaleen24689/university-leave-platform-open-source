package com.example.leaveplatform.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * The window is always passed as two real instants; when the reader left the
     * dates blank the service supplies the widest range the table can hold. A
     * nullable date parameter would reach Oracle as a bind whose type it has to
     * guess, and the comparison would fail before it returned a single row.
     *
     * <p>An action is stored as {@code LEAVE_APPROVED} but shown as "Leave
     * approved", so both spellings are matched and the guide can document either.
     */
    @Query("""
            select e from AuditEvent e
            where e.occurredAt >= :from and e.occurredAt < :to
              and (lower(e.actorUsername) like lower(concat('%', :term, '%'))
                or lower(e.action) like lower(concat('%', :term, '%'))
                or replace(lower(e.action), '_', ' ') like lower(concat('%', :term, '%'))
                or lower(e.targetType) like lower(concat('%', :term, '%'))
                or replace(lower(e.targetType), '_', ' ') like lower(concat('%', :term, '%'))
                or lower(coalesce(e.targetId, '')) like lower(concat('%', :term, '%'))
                or lower(coalesce(e.detail, '')) like lower(concat('%', :term, '%')))
            order by e.occurredAt desc, e.id desc
            """)
    List<AuditEvent> search(@Param("term") String term, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    @Query("""
            select e from AuditEvent e
            where e.occurredAt >= :from and e.occurredAt < :to
            order by e.occurredAt desc, e.id desc
            """)
    List<AuditEvent> findInWindow(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
