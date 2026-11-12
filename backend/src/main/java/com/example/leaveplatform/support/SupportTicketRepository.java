package com.example.leaveplatform.support;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Two shapes of list, matching the two questions the platform asks: what did I
 * ask, and what is waiting for us. Each statement names its own condition
 * instead of taking a nullable filter, for the reason already written up under
 * {@code leave/} - Oracle has to guess the type of a null parameter, and a
 * department-wide queue is not the place to find out at run time.
 */
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @Query("""
            select t from SupportTicket t
            where t.openedBy.id = :employeeId
            order by t.createdAt desc, t.id desc
            """)
    List<SupportTicket> findLatestByEmployee(@Param("employeeId") Long employeeId, Pageable pageable);

    /** The desk's queue, oldest first: the line waiting longest is the one to answer. */
    @Query("""
            select t from SupportTicket t
            where t.status in :statuses
            order by t.createdAt asc, t.id asc
            """)
    List<SupportTicket> findQueue(@Param("statuses") Collection<TicketStatus> statuses, Pageable pageable);

    /** Every ticket, newest first, for the administrator who wants the whole picture. */
    @Query("""
            select t from SupportTicket t
            order by t.createdAt desc, t.id desc
            """)
    List<SupportTicket> findLatest(Pageable pageable);

    long countByStatusIn(Collection<TicketStatus> statuses);

    /**
     * Locked because two people at the desk can open the same ticket at the same
     * moment; without the lock the second answer would quietly overwrite the
     * first status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findByIdForUpdate(@Param("id") Long id);
}
