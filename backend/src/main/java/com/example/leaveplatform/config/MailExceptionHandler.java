package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.mail.MailSettingException;

/**
 * Turns a mail setting the platform will not keep into a readable 400.
 *
 * <p>The message is always about the caller's own entry - an out-of-range port, a
 * missing key, an address with no name part - so returning it unchanged helps the
 * person fix it. Reporting only "Bad request" would leave them guessing which of
 * six boxes is wrong.
 */
@RestControllerAdvice
class MailExceptionHandler {

    @ExceptionHandler(MailSettingException.class)
    ProblemDetail rejected(MailSettingException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Mail settings not saved");
        return problem;
    }
}
