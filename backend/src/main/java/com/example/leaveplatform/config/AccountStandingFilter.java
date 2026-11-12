package com.example.leaveplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.leaveplatform.identity.AppUserRepository;

/**
 * Ends the session of an account that has been switched off since it signed in.
 *
 * <p>{@code enabled} and {@code employee.active} are read once, at sign-in, when
 * Spring Security builds the principal. Nothing re-reads them afterwards, so
 * without this filter a person who leaves, or whose account is disabled during
 * an incident, keeps approving leave and reading reports until the idle timeout
 * or the eight-hour limit happens to catch up - up to a whole working day.
 *
 * <p>The lock is deliberately not part of this check. It is set by five wrong
 * passwords at the sign-in screen, which anybody can trigger against a username
 * they do not own; ending live sessions on it would let a stranger sign a
 * colleague out at will. It still refuses the next sign-in, which is what it is
 * for.
 *
 * <p>One indexed lookup per authenticated request. Anonymous visitors, static
 * resources and the sign-in page cost nothing.
 */
public class AccountStandingFilter extends OncePerRequestFilter {

    static final String LOGOUT_PATH = "/logout";

    private final AppUserRepository users;

    public AccountStandingFilter(AppUserRepository users) {
        this.users = users;
    }

    /**
     * The two doors back in stay open. Refusing them would leave a person whose
     * account was just switched off unable to reach the sign-in screen at all,
     * which is the one thing they still need.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return CaptchaEnforcementFilter.LOGIN_PATH.equals(path) || LOGOUT_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String username = signedInUsername();
        if (username == null || stillAllowedIn(username)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"This account is no longer active, please sign in again\"}");
    }

    private boolean stillAllowedIn(String username) {
        return users.findByUsername(username)
                .map(user -> user.isEnabled() && user.getEmployee().isActive())
                .orElse(false);
    }

    private static String signedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}