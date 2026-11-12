package com.example.leaveplatform.leave;

import jakarta.validation.constraints.Size;

/**
 * The final decision on a request. {@code compensate} approves the leave as
 * compensatory leave, which is what the earlier schema called {@code isCompensated}: the
 * request is granted but no days come off the casual leave balance.
 */
public record LeaveDecisionRequest(
        boolean approve,
        boolean compensate,
        @Size(max = 1000) String remarks) {
}
