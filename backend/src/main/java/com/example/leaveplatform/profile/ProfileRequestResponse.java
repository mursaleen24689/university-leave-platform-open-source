package com.example.leaveplatform.profile;

import java.time.Instant;

/** One request as a list line and as the API returns it. */
public record ProfileRequestResponse(
        Long id,
        String reference,
        ProfileRequestStatus status,
        String statusLabel,
        String changedFields,
        String changedLabels,
        int fieldCount,
        String reason,
        String employeeNo,
        String employeeName,
        String departmentName,
        String decidedByName,
        String decisionNote,
        Instant createdAt,
        Instant updatedAt,
        Instant decidedAt) {

    public static ProfileRequestResponse of(ProfileRequest request) {
        return new ProfileRequestResponse(
                request.getId(),
                request.getReference(),
                request.getStatus(),
                request.getStatus().label(),
                ProfileField.join(request.changedFields()),
                ProfileField.describe(request.changedFields()),
                request.changedFields().size(),
                request.getReason(),
                request.getEmployee().getEmployeeNo(),
                request.getEmployee().getFullName(),
                request.getEmployee().getDepartment().getName(),
                request.getDecidedBy() == null ? null : request.getDecidedBy().getFullName(),
                request.getDecisionNote(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getDecidedAt());
    }
}
