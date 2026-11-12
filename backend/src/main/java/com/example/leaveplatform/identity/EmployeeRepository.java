package com.example.leaveplatform.identity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"department"})
    Optional<Employee> findByEmployeeNo(String employeeNo);

    boolean existsByEmployeeNo(String employeeNo);

    boolean existsByEmail(String email);

    Optional<Employee> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"department"})
    List<Employee> findByDepartmentIdOrderByFullNameAsc(Long departmentId);

    /**
     * The people a staff summary is built from. An employee who has left is not
     * on a sheet somebody signs, but their past requests still appear in the
     * leave register.
     */
    @EntityGraph(attributePaths = {"department"})
    List<Employee> findByActiveTrueOrderByFullNameAsc();

    @EntityGraph(attributePaths = {"department"})
    List<Employee> findByDepartmentIdAndActiveTrueOrderByFullNameAsc(Long departmentId);

    /**
     * The record of an employee who is still on the staff, by id. A delegated
     * window may only be opened onto a live colleague, so the id coming back in
     * from a screen or a request body is checked here rather than trusted.
     */
    @EntityGraph(attributePaths = {"department"})
    Optional<Employee> findByIdAndActiveTrue(Long id);

    /**
     * The colleagues a delegating officer can pick. A window is only worth
     * opening onto somebody who can read the bell and sign in, so the enabled
     * account is part of the test, not a later surprise. {@code pattern} is
     * matched against the name and the staff number; pass null for the first
     * page of everybody.
     */
    @EntityGraph(attributePaths = {"department"})
    @Query("""
            select e from Employee e
            where e.active = true
              and exists (select u.id from AppUser u
                          where u.employee.id = e.id and u.enabled = true)
              and (:pattern is null
                   or lower(e.fullName) like :pattern
                   or lower(e.employeeNo) like :pattern)
            order by e.fullName asc
            """)
    List<Employee> findSignable(@Param("pattern") String pattern, Pageable limit);

    /**
     * Whether anybody's reporting line points at this person. Only someone with
     * direct reports has requests of the supervisor kind sent to them, so only
     * they can hand that approving over - see {@code V016}.
     */
    boolean existsBySupervisorEmployeeIdAndActiveTrue(Long supervisorEmployeeId);

    /**
     * Administration search. {@code pattern} is matched against the name and
     * the staff number; pass null to list everyone in the department. The
     * caller passes a page request because a whole university staff list is
     * more than any screen can show.
     */
    @EntityGraph(attributePaths = {"department"})
    @Query("""
            select e from Employee e
            where (:departmentId is null or e.department.id = :departmentId)
              and (:pattern is null
                   or lower(e.fullName) like :pattern
                   or lower(e.employeeNo) like :pattern)
            order by e.fullName asc
            """)
    List<Employee> search(@Param("departmentId") Long departmentId, @Param("pattern") String pattern,
            Pageable limit);

    /**
     * Active employees who still need a sign-in account, for the account form.
     */
    @EntityGraph(attributePaths = {"department"})
    @Query("""
            select e from Employee e
            where e.active = true
              and not exists (select u.id from AppUser u where u.employee.id = e.id)
            order by e.fullName asc
            """)
    List<Employee> findWithoutAccount();

    /**
     * The record with a write lock on it, for the moment a proposed correction is
     * approved. A plain read is not enough there: an ordinary update writes the
     * whole row, so an office edit and an approval that read the same record at
     * the same moment would let whichever committed second overwrite the other
     * without either of them noticing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
