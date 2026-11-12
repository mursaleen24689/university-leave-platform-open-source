package com.example.leaveplatform.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = {"roles", "employee", "employee.department"})
    Optional<AppUser> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "employee", "employee.department"})
    Optional<AppUser> findByEmployeeId(Long employeeId);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * The enabled accounts holding one role, and the enabled ones holding it in
     * one department. A leave event that no single named person owns - the final
     * decision for a department, for instance - goes to everyone who can take it,
     * so the queue has to be able to name them.
     */
    @EntityGraph(attributePaths = {"roles", "employee", "employee.department"})
    @Query("""
            select u from AppUser u
            join u.roles r
            join u.employee e
            where r.code = :roleCode and u.enabled = true and e.active = true
            order by u.username
            """)
    List<AppUser> findEnabledWithRole(@Param("roleCode") String roleCode);

    @EntityGraph(attributePaths = {"roles", "employee", "employee.department"})
    @Query("""
            select u from AppUser u
            join u.roles r
            join u.employee e
            where r.code = :roleCode and u.enabled = true and e.active = true
              and e.department.id = :departmentId
            order by u.username
            """)
    List<AppUser> findEnabledWithRoleInDepartment(@Param("roleCode") String roleCode,
            @Param("departmentId") Long departmentId);

    /**
     * Administration search over the username and the employee's name; pass
     * null to list the first page of every account. The caller passes a page
     * request because this query joins roles, employee and department, and an
     * empty search box must not load the whole register into the browser.
     */
    @EntityGraph(attributePaths = {"roles", "employee", "employee.department"})
    @Query("""
            select u from AppUser u
            where (:pattern is null
                   or lower(u.username) like :pattern
                   or lower(u.employee.fullName) like :pattern)
            order by u.employee.fullName asc
            """)
    List<AppUser> search(@Param("pattern") String pattern, Pageable limit);
}
