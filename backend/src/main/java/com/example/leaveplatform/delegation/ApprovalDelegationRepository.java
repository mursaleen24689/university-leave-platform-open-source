package com.example.leaveplatform.delegation;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The questions the platform asks about handed-over approving: what may this
 * person do today, what has this person seen either side of, and does a new
 * window collide with one that has not been handed back.
 *
 * <p>"Held" and "usable today" are different tests, and both appear here on
 * purpose. A revoked row is held by nobody; a row whose window closed last month
 * is still unhanded-back but usable by nobody. The leave queues always ask for
 * the second - {@code active} plus today between the dates - so a forgotten
 * window cannot keep accepting decisions, and a future one cannot empty a queue
 * early.
 */
public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Long> {

    /**
     * The windows this person has seen, held or handed over, usable ones first -
     * one list for one screen, because "what about me" is the question.
     */
    @Query("""
            select d from ApprovalDelegation d
            where (d.fromEmployee.id = :employeeId or d.toEmployee.id = :employeeId)
            order by d.active desc, d.startDate desc, d.id desc
            """)
    List<ApprovalDelegation> findMine(@Param("employeeId") Long employeeId, Pageable pageable);

    /** Every window, the unhanded-back ones first, for the office's reading. */
    @Query("""
            select d from ApprovalDelegation d
            order by d.active desc, d.startDate desc, d.id desc
            """)
    List<ApprovalDelegation> findLatest(Pageable pageable);

    /**
     * The windows held over one person's own approving, revoked ones last. The
     * service tests these for "usable today" and for date collisions; one
     * person holds a handful at the very most, so this is not a hot query.
     */
    @Query("""
            select distinct d from ApprovalDelegation d
            join fetch d.toEmployee t
            left join fetch d.department
            where d.fromEmployee.id = :employeeId
            order by d.active desc, d.startDate desc, d.id desc
            """)
    List<ApprovalDelegation> findHeld(@Param("employeeId") Long employeeId);

    /**
     * What this person may act under on the given day, as a delegatee, in one
     * kind of approving. The leave queues ask it once per screen.
     */
    @Query("""
            select distinct d from ApprovalDelegation d
            join fetch d.fromEmployee f
            join fetch d.toEmployee t
            left join fetch d.department
            where d.active = true
              and d.scope = :scope
              and t.id = :delegateeId
              and d.startDate <= :day
              and d.endDate >= :day
            order by d.id asc
            """)
    List<ApprovalDelegation> findUsableAs(@Param("delegateeId") Long delegateeId,
            @Param("scope") DelegationScope scope, @Param("day") LocalDate day);

    /**
     * Locked, because two decisions about one window made at the same moment -
     * two revokes, or a revoke beside an early end - must not both report
     * success. Creation is protected the same way on the delegator's staff row;
     * see {@link com.example.leaveplatform.identity.EmployeeRepository#findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ApprovalDelegation d where d.id = :id")
    Optional<ApprovalDelegation> findByIdForUpdate(@Param("id") Long id);
}
