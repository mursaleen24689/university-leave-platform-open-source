package com.example.leaveplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.leaveplatform.auth.CaptchaService;

/**
 * Refuses a sign-in attempt that has not answered the security code, before
 * the password is looked at.
 *
 * <p>Ordering is the whole point of this filter. Spring Security's CSRF filter
 * has already accepted the request, and this one runs before
 * {@code UsernamePasswordAuthenticationFilter}, so an attempt with no code
 * never reaches the database, never compares a hash and never produces the
 * "wrong password" page that would tell an outsider the account exists. The
 * answer is a redirect back to the sign-in page saying which problem the code
 * had, which is how the screen knows what to say.
 */
public class CaptchaEnforcementFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/login";

    private final CaptchaService captcha;

    public CaptchaEnforcementFilter(CaptchaService captcha) {
        this.captcha = captcha;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String reason = session == null
                ? CaptchaService.CODE_MISSING
                : captcha.check(session, request.getParameter(CaptchaService.CODE_PARAMETER));
        if (!CaptchaService.PASSED.equals(reason)) {
            response.sendRedirect(request.getContextPath() + LOGIN_PATH
                    + "?" + CaptchaService.REASON_PARAMETER + "=" + reason);
            return;
        }
        chain.doFilter(request, response);
    }
}
