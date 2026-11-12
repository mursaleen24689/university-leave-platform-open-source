package com.example.leaveplatform.leave;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, int year);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    List<LeaveBalance> findByEmployeeIdOrderByYearDesc(Long employeeId);

    /**
     * Row lock taken before a deduction so two approvals at the same moment
     * cannot both pass the sufficient-balance check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from LeaveBalance b
            where b.employee.id = :employeeId
              and b.leaveType.id = :leaveTypeId
              and b.year = :year
            """)
    Optional<LeaveBalance> findForUpdate(@Param("employeeId") Long employeeId,
            @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    /**
     * One employee's allocation for the year, without loading the employee. The
     * names are read as properties by Spring Data, so they keep the {@code get}
     * spelling a bean reader expects.
     */
    interface YearBalance {

        Long getEmployeeId();

        int getAllocated();

        int getUsed();
    }

    /**
     * Every balance row for one leave type and year, for the staff summary. The
     * summary is built from the employee list, so an employee with no row yet
     * still appears - with the full allowance and nothing used - instead of
     * silently missing from the department's sheet.
     */
    @Query("""
            select b.employee.id as employeeId, b.allocated as allocated, b.used as used
            from LeaveBalance b
            where b.leaveType.id = :leaveTypeId and b.year = :year
              and (:departmentId is null or b.employee.department.id = :departmentId)
            """)
    List<YearBalance> findAllForYear(@Param("leaveTypeId") Long leaveTypeId, @Param("year") int year,
            @Param("departmentId") Long departmentId);
}
