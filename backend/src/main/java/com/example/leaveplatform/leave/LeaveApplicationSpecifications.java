package com.example.leaveplatform.leave;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * The pieces a leave list is filtered by. They are combined per request rather
 * than written as SQL with "parameter is null" tests, because Oracle refuses an
 * untyped null bind in a date comparison; leaving the clause out is both shorter
 * and something the database can use an index for.
 *
 * <p>Public because the leave register asks the same questions of the same
 * table; the filter must not be able to read one way on the screen and another
 * way in the export.
 */
public final class LeaveApplicationSpecifications {

    private LeaveApplicationSpecifications() {
    }

    public static Specification<LeaveApplication> employee(Long employeeId) {
        return (root, query, builder) ->
                builder.equal(root.<Long>get("employee").get("id"), employeeId);
    }

    public static Specification<LeaveApplication> supervisor(Long employeeId) {
        return (root, query, builder) ->
                builder.equal(root.<Long>get("supervisorEmployeeId"), employeeId);
    }

    /**
     * Requests sent to any of these supervisors. A delegatee's queue is their
     * own name plus the names they are standing in for today, so the widen
     * happens here and nowhere else - a button and the list it sits beside read
     * the same clause.
     */
    public static Specification<LeaveApplication> supervisorIn(Collection<Long> employeeIds) {
        return (root, query, builder) ->
                root.<Long>get("supervisorEmployeeId").in(employeeIds);
    }

    public static Specification<LeaveApplication> department(Long departmentId) {
        return (root, query, builder) ->
                builder.equal(root.<Long>get("employee").get("department").get("id"), departmentId);
    }

    /** The same widen as {@link #supervisorIn}, for a final-decision queue. */
    public static Specification<LeaveApplication> departmentIn(Collection<Long> departmentIds) {
        return (root, query, builder) ->
                root.<Long>get("employee").get("department").get("id").in(departmentIds);
    }

    public static Specification<LeaveApplication> status(LeaveStatus status) {
        return (root, query, builder) -> builder.equal(root.<LeaveStatus>get("status"), status);
    }

    /**
     * The period and status a person asked for. Only the parts they filled in
     * become a clause, so an empty filter leaves the list as it was.
     */
    public static Specification<LeaveApplication> filter(LeaveFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> parts = new ArrayList<>();
            if (filter.from() != null) {
                parts.add(builder.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), filter.from()));
            }
            if (filter.to() != null) {
                parts.add(builder.lessThanOrEqualTo(root.<LocalDate>get("startDate"), filter.to()));
            }
            if (filter.status() != null) {
                parts.add(builder.equal(root.<LeaveStatus>get("status"), filter.status()));
            }
            return builder.and(parts.toArray(Predicate[]::new));
        };
    }
}
