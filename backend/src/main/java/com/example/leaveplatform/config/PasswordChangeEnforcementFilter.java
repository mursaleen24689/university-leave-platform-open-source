package com.example.leaveplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;

/**
 * Holds an account that still has its issued temporary password to the endpoints
 * it needs in order to change it.
 *
 * <p>{@code ui.PasswordChangeGuard} already reroutes every screen to the change
 * page, but it is a Vaadin navigation listener: a person who signs in and then
 * talks to {@code /api/**} directly never navigates, so the guard never runs and
 * the temporary password - which the office knows, and which may have travelled
 * by e-mail or on paper - keeps working for leave, approvals and administration.
 * This is the same rule on the other door.
 *
 * <p>The flag is read from the database per request rather than from the session
 * principal, which is loaded once at sign-in and would stay stale after the
 * change. Only the API is filtered: the screens have their own guard, and
 * filtering Vaadin's internal requests here would fight it.
 *
 * <p>{@code /api/v1/auth/**} is exempt as a whole, because those are the calls a
 * person on a temporary password needs - who they are, a CSRF token, and the
 * change itself. Two-step enrolment sits under that prefix and refuses a
 * temporary password on its own, so the exemption does not open a second way in.
 */
public class PasswordChangeEnforcementFilter extends OncePerRequestFilter {

    static final String API_PREFIX = "/api/";
    static final String AUTH_PREFIX = "/api/v1/auth/";

    /** Fixed sentences, so nothing a caller typed can reach the response body. */
    private static final String REFUSAL = "{\"title\":\"Password change required\",\"status\":403,"
            + "\"detail\":\"This account is still using the temporary password it was issued."
            + " Change it at /api/v1/auth/password before using the rest of the platform.\"}";

    private final AppUserRepository users;

    public PasswordChangeEnforcementFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path == null || !path.startsWith(API_PREFIX) || path.startsWith(AUTH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (mustChangePassword()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(REFUSAL);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // Not signed in: the authorization rules decide this request, not this filter.
            return false;
        }
        return users.findByUsername(authentication.getName())
                .map(AppUser::isMustChangePassword)
                .orElse(false);
    }
}
