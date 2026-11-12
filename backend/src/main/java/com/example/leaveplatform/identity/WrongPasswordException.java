package com.example.leaveplatform.identity;

public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("Current password is incorrect.");
    }
}
