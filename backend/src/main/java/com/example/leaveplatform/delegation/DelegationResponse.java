package com.example.leaveplatform.delegation;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One window as a list line and as the API returns it. {@code mine} says whether
 * the person reading it is the one who handed the approving over - the only one
 * the screen offers a revoke button to - and {@code state} is the sentence for
 * where the window stands today, because "live" and "ended" are not columns.
 */
public record DelegationResponse(
        Long id,
        String reference,
        DelegationScope scope,
        String scopeLabel,
        Long fromEmployeeId,
        String fromName,
        String fromEmployeeNo,
        Long toEmployeeId,
        String toName,
        String toEmployeeNo,
        String departmentName,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        String state,
        boolean mine,
        boolean usableToday,
        String reason,
        String revokedByName,
        Instant revokedAt,
        Instant createdAt) {

    public static DelegationResponse of(ApprovalDelegation delegation, Long viewerEmployeeId) {
        LocalDate today = LocalDate.now();
        return new DelegationResponse(
                delegation.getId(),
                delegation.getReference(),
                delegation.getScope(),
                delegation.getScope().label(),
                delegation.getFromEmployee().getId(),
                delegation.getFromEmployee().getFullName(),
                delegation.getFromEmployee().getEmployeeNo(),
                delegation.getToEmployee().getId(),
                delegation.getToEmployee().getFullName(),
                delegation.getToEmployee().getEmployeeNo(),
                delegation.getDepartment() == null ? null : delegation.getDepartment().getName(),
                delegation.getStartDate(),
                delegation.getEndDate(),
                delegation.isActive(),
                delegation.state(today),
                viewerEmployeeId != null
                        && viewerEmployeeId.equals(delegation.getFromEmployee().getId()),
                delegation.covers(today),
                delegation.getReason(),
                delegation.getRevokedBy() == null ? null : delegation.getRevokedBy().getFullName(),
                delegation.getRevokedAt(),
                delegation.getCreatedAt());
    }
}
