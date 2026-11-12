package com.example.leaveplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionAbsoluteExpiryFilterTest {

    private final SessionAbsoluteExpiryFilter filter = new SessionAbsoluteExpiryFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stampsTheSignInTimeOnTheFirstSignedInRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_CENTRAL_ADMIN"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getSession().getAttribute(SessionAbsoluteExpiryFilter.AUTHENTICATED_AT))
                .isInstanceOf(Instant.class);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doesNotStampAnAnonymousRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getSession().getAttribute(SessionAbsoluteExpiryFilter.AUTHENTICATED_AT))
                .isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsAFreshSessionThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true)
                .setAttribute(SessionAbsoluteExpiryFilter.AUTHENTICATED_AT, Instant.now());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void endsASessionOlderThanEightHours() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(
                SessionAbsoluteExpiryFilter.AUTHENTICATED_AT, Instant.now().minusSeconds(8 * 3600 + 60));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void ignoresRequestsWithoutASession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
