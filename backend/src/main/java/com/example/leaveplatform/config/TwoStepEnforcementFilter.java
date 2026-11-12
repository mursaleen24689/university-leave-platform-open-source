package com.example.leaveplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.leaveplatform.auth.TwoStepService;

/**
 * Refuses a sign-in attempt whose authenticator code does not match, before the
 * password is looked at.
 *
 * <p>The ordering is the design, and it is worth reading twice. This filter
 * runs after {@link CaptchaEnforcementFilter} and before Spring Security's
 * password filter, so:
 *
 * <ul>
 *   <li>every attempt that gets here has already answered a security code, and
 *   the counts on that code - pictures per address, pictures per browser, three
 *   wrong answers and a wait - are what cap guessing at a six-digit number. An
 *   enrolled account is not a softer target than an unenrolled one.</li>
 *   <li>a wrong code never reaches the password hash, so the attempt cannot
 *   tell anybody whether the password it came with was right. The answer is the
 *   same page a wrong password produces: {@code /login?error}, nothing more.</li>
 *   <li>a correct code with a wrong password still fails, because this filter
 *   grants nothing - it only declines to refuse. Whoever holds the phone is
 *   back at the beginning.</li>
 * </ul>
 *
 * <p>Checking the number before the password rather than after it is also what
 * keeps this filter short. The alternative - promote the session after
 * authentication and ask for the code on a second screen - needs a place to
 * park a half-authenticated session, a second screen with its own limits, and a
 * way to say "your password was right" out loud. All three are things this
 * platform would rather not have to get right.
 */
public class TwoStepEnforcementFilter extends OncePerRequestFilter {

    /** Spring Security's own name for the field, which the sign-in form posts. */
    static final String USERNAME_PARAMETER = "username";

    private final TwoStepService twoStep;

    public TwoStepEnforcementFilter(TwoStepService twoStep) {
        this.twoStep = twoStep;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && CaptchaEnforcementFilter.LOGIN_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        TwoStepService.CodeResult result = twoStep.checkSignInCode(
                request.getParameter(USERNAME_PARAMETER),
                request.getParameter(TwoStepService.CODE_PARAMETER));
        if (result == TwoStepService.CodeResult.REFUSED
                || result == TwoStepService.CodeResult.SEED_UNREADABLE) {
            // The same address a wrong password sends the browser to, with no
            // hint that a code was even part of the attempt.
            response.sendRedirect(request.getContextPath() + CaptchaEnforcementFilter.LOGIN_PATH
                    + "?error");
            return;
        }
        chain.doFilter(request, response);
    }
}
