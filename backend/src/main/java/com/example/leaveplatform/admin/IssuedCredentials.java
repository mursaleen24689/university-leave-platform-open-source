package com.example.leaveplatform.admin;

/**
 * Result of creating an account or resetting its password. The temporary
 * password is returned once, for the administrator to hand over; it is stored
 * only as a BCrypt hash.
 */
public record IssuedCredentials(AccountSummary account, String temporaryPassword) {
}
