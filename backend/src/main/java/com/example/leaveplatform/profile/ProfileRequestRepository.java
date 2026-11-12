package com.example.leaveplatform.profile;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The two lists the platform asks about a proposed correction: what did I ask
 * for, and what is waiting for the office.
 */
public interface ProfileRequestRepository extends JpaRepository<ProfileRequest, Long> {

    @Query("""
            select r from ProfileRequest r
            where r.employee.id = :employeeId
            order by r.createdAt desc, r.id desc
            """)
    List<ProfileRequest> findLatestByEmployee(@Param("employeeId") Long employeeId, Pageable pageable);

    /** The office's queue, longest waiting first - the request closest to being overdue. */
    @Query("""
            select r from ProfileRequest r
            where r.status = :status
            order by r.createdAt asc, r.id asc
            """)
    List<ProfileRequest> findQueue(@Param("status") ProfileRequestStatus status, Pageable pageable);

    /** Every request, newest first, for the person who wants the whole picture. */
    @Query("""
            select r from ProfileRequest r
            order by r.createdAt desc, r.id desc
            """)
    List<ProfileRequest> findLatest(Pageable pageable);

    Optional<ProfileRequest> findFirstByEmployeeIdAndStatus(Long employeeId, ProfileRequestStatus status);

    long countByStatus(ProfileRequestStatus status);

    /**
     * Locked, because two people at the office can open the same request at the
     * same moment and both press approve. The second one then finds the row no
     * longer pending instead of applying the same change twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProfileRequest r where r.id = :id")
    Optional<ProfileRequest> findByIdForUpdate(@Param("id") Long id);
}
