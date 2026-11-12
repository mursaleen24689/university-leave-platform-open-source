package com.example.leaveplatform.leave;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Lists are read through {@link JpaSpecificationExecutor} so a period or status
 * that the caller left blank simply does not appear in the SQL, instead of being
 * compared against a null the database has to guess the type of.
 */
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long>,
        JpaSpecificationExecutor<LeaveApplication> {

    @Query("""
            select count(a) > 0 from LeaveApplication a
            where a.employee.id = :employeeId
              and a.status in :statuses
              and a.startDate <= :endDate
              and a.endDate >= :startDate
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<LeaveStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LeaveApplication a where a.id = :id")
    Optional<LeaveApplication> findByIdForUpdate(@Param("id") Long id);

    /**
     * The read a printed form is checked against. A piece of paper carries the
     * reference and nothing else, so this is the only way in from outside the
     * platform; the unique index on the column is what makes it one lookup.
     */
    Optional<LeaveApplication> findByReference(String reference);

    /**
     * Days and requests counted per employee and status, for the staff summary.
     * The names are read as properties by Spring Data, so they keep the
     * {@code get} spelling a bean reader expects.
     */
    interface EmployeeStatusTotals {

        Long getEmployeeId();

        LeaveStatus getStatus();

        Long getDays();

        Long getRequests();
    }

    /**
     * The counting for the staff summary is done by the database. Loading a
     * whole department's year of requests to add them up in Java would read
     * thousands of rows to produce a handful of numbers.
     */
    @Query("""
            select a.employee.id as employeeId, a.status as status,
                   sum(a.days) as days, count(a.id) as requests
            from LeaveApplication a
            where a.endDate >= :yearStart and a.startDate <= :yearEnd
              and (:departmentId is null or a.employee.department.id = :departmentId)
            group by a.employee.id, a.status
            """)
    List<EmployeeStatusTotals> totalsForYear(@Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd, @Param("departmentId") Long departmentId);

    /**
     * Days already spent with the given status in one calendar year, for the
     * printed form's "leave availed previously" line. The request being printed
     * is left out by id: the sentence sits above the decision, so counting the
     * days it is asking for would state that the applicant has already taken
     * them.
     */
    @Query("""
            select coalesce(sum(a.days), 0) from LeaveApplication a
            where a.employee.id = :employeeId
              and a.status = :status
              and a.endDate >= :yearStart and a.startDate <= :yearEnd
              and a.id <> :excludingId
            """)
    int daysInYear(@Param("employeeId") Long employeeId, @Param("status") LeaveStatus status,
            @Param("yearStart") LocalDate yearStart, @Param("yearEnd") LocalDate yearEnd,
            @Param("excludingId") Long excludingId);
}
