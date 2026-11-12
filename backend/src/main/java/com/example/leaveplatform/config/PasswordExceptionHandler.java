package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.identity.WeakPasswordException;
import com.example.leaveplatform.identity.WrongPasswordException;

/**
 * Turns password-change failures into a readable 400. The message is safe to
 * return because it describes the caller's own request, and it is never logged
 * with the password.
 */
@RestControllerAdvice
class PasswordExceptionHandler {

    @ExceptionHandler(WeakPasswordException.class)
    ProblemDetail weakPassword(WeakPasswordException exception) {
        return detail("Password not accepted", exception.getMessage());
    }

    @ExceptionHandler(WrongPasswordException.class)
    ProblemDetail wrongPassword(WrongPasswordException exception) {
        return detail("Password not changed", exception.getMessage());
    }

    private static ProblemDetail detail(String title, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problem.setTitle(title);
        return problem;
    }
}
