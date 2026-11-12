package com.example.leaveplatform.support;

import java.time.Instant;

/** One ticket as the screens and the API present it. */
public record TicketResponse(
        Long id,
        String reference,
        String subject,
        TicketCategory category,
        String categoryLabel,
        TicketStatus status,
        String statusLabel,
        String employeeNo,
        String openedByName,
        String departmentName,
        String assigneeName,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public static TicketResponse of(SupportTicket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getReference(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getCategory().label(),
                ticket.getStatus(),
                ticket.getStatus().label(),
                ticket.getOpenedBy().getEmployeeNo(),
                ticket.getOpenedBy().getFullName(),
                ticket.getDepartment().getName(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getFullName(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt());
    }
}
