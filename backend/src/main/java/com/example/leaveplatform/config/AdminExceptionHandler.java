package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.admin.AdminException;

/**
 * Turns a data-entry problem into a readable 400 for the API. The message is
 * about the caller's own input, so it is safe to return unchanged.
 */
@RestControllerAdvice
class AdminExceptionHandler {

    @ExceptionHandler(AdminException.class)
    ProblemDetail rejected(AdminException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Record not saved");
        return problem;
    }
}
