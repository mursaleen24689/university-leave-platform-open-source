package com.example.leaveplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a session eight hours after sign-in, whatever the idle timeout says.
 * The idle timeout alone would keep a machine that is used all day signed in
 * indefinitely. It runs after {@code SecurityContextHolderFilter}, so the
 * authenticated principal is read from the security context rather than from
 * {@code request.getUserPrincipal()}, which is only populated later in the
 * chain.
 */
public class SessionAbsoluteExpiryFilter extends OncePerRequestFilter {

    static final String AUTHENTICATED_AT = "com.example.authenticatedAt";
    static final Duration MAX_SESSION = Duration.ofHours(8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && isSignedIn() && session.getAttribute(AUTHENTICATED_AT) == null) {
            session.setAttribute(AUTHENTICATED_AT, Instant.now());
        }
        if (session != null && expired(session)) {
            session.invalidate();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Session expired, please sign in again\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSignedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static boolean expired(HttpSession session) {
        return session.getAttribute(AUTHENTICATED_AT) instanceof Instant signedInAt
                && Instant.now().isAfter(signedInAt.plus(MAX_SESSION));
    }
}
