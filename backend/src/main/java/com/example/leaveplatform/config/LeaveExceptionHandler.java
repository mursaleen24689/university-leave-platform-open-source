package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.leave.LeaveRuleException;

/**
 * Turns a broken leave rule into a readable 400. The message describes the
 * caller's own request, so it is safe to return unchanged.
 */
@RestControllerAdvice
class LeaveExceptionHandler {

    @ExceptionHandler(LeaveRuleException.class)
    ProblemDetail ruleBroken(LeaveRuleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Leave request not accepted");
        return problem;
    }
}
