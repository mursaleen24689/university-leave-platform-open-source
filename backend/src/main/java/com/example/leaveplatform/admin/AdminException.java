package com.example.leaveplatform.admin;

/**
 * A data-entry problem the administrator can fix, such as a duplicate staff
 * number or an unknown department code. The message is meant for the screen.
 */
public class AdminException extends RuntimeException {

    public AdminException(String message) {
        super(message);
    }
}
