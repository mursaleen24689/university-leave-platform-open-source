package com.example.leaveplatform.admin;

import java.util.List;

/**
 * What the administrator supplies for a new sign-in account. The temporary
 * password may be blank, in which case the server generates one.
 */
public record AccountInput(
        Long employeeId,
        String username,
        String temporaryPassword,
        List<String> roles) {
}
