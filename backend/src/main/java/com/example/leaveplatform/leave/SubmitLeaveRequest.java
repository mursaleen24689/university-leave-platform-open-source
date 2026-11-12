package com.example.leaveplatform.leave;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * The browser sends dates only; the server computes the day count from the
 * holiday calendar so a tampered request cannot claim fewer days.
 */
public record SubmitLeaveRequest(
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 1000) String reason) {
}
