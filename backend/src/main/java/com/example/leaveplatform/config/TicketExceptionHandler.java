package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.support.TicketRuleException;

/**
 * Turns a ticket that cannot be accepted or cannot go on into a readable 400.
 * The message describes the caller's own request, so it is safe to return
 * unchanged.
 */
@RestControllerAdvice
class TicketExceptionHandler {

    @ExceptionHandler(TicketRuleException.class)
    ProblemDetail ruleBroken(TicketRuleException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Help-desk ticket not accepted");
        return problem;
    }
}
