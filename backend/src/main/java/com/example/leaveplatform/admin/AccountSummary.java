package com.example.leaveplatform.admin;

import java.time.Instant;
import java.util.List;

/**
 * One sign-in account as the administrator sees it. The password is never
 * included, and neither is the authenticator seed - only whether there is one
 * and when it was confirmed, which is what an office needs in order to help a
 * person who cannot sign in any more.
 */
public record AccountSummary(
        Long id,
        Long employeeId,
        String employeeNo,
        String employeeName,
        String departmentCode,
        String username,
        boolean enabled,
        boolean mustChangePassword,
        List<String> roles,
        Instant lastLoginAt,
        boolean twoStepEnabled,
        Instant twoStepSince) {
}
