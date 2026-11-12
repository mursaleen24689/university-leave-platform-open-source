package com.example.leaveplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;

/**
 * The temporary-password rule on the door the screens do not guard: a caller who
 * signs in and then talks to the API directly never navigates, so the Vaadin
 * reroute never runs and the issued password would keep working for everything.
 */
class PasswordChangeEnforcementFilterTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUser user = mock(AppUser.class);
    private final PasswordChangeEnforcementFilter filter = new PasswordChangeEnforcementFilter(users);

    @AfterEach
    void clearTheSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesAnAccountStillUsingThePasswordItWasIssued() throws Exception {
        givenATemporaryPassword();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("Password change required");
        assertThat(response.getContentAsString()).contains("/api/v1/auth/password");
    }

    @Test
    void refusesItOnTheAdministrationApiAsWell() throws Exception {
        givenATemporaryPassword();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("POST", "/api/v1/admin/accounts"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void letsTheSameAccountReadWhoItIsAndChangeThePassword() throws Exception {
        givenATemporaryPassword();

        for (MockHttpServletRequest request : List.of(
                api("GET", "/api/v1/auth/me"),
                api("GET", "/api/v1/auth/csrf"),
                api("POST", "/api/v1/auth/password"))) {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }
        // The exemption is the whole auth prefix, so no lookup is spent on it.
        verifyNoInteractions(users);
    }

    @Test
    void leavesTwoStepEnrolmentToItsOwnTemporaryPasswordRule() throws Exception {
        givenATemporaryPassword();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("POST", "/api/v1/auth/two-step/enrolment"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    @Test
    void letsAnAccountThatHasChangedItsPasswordThrough() throws Exception {
        signIn();
        when(users.findByUsername("ayesha.khan")).thenReturn(Optional.of(user));
        when(user.isMustChangePassword()).thenReturn(false);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void leavesAnAnonymousVisitorToTheAuthorizationRules() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    @Test
    void leavesASignedOutRequestAlone() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    @Test
    void leavesTheScreensToTheirOwnGuard() throws Exception {
        givenATemporaryPassword();
        MockHttpServletRequest screen = new MockHttpServletRequest("GET", "/my-leave");
        screen.setServletPath("/my-leave");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(screen, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    @Test
    void doesNotTrapASessionWhoseAccountHasBeenRemoved() throws Exception {
        signIn();
        when(users.findByUsername("ayesha.khan")).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void readsThePathTheServletSawRatherThanTheWholeAddress() throws Exception {
        givenATemporaryPassword();
        MockHttpServletRequest request = api("GET", "/api/v1/leave");
        request.setContextPath("/leave");
        request.setRequestURI("/leave/api/v1/leave");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private void givenATemporaryPassword() {
        signIn();
        when(users.findByUsername("ayesha.khan")).thenReturn(Optional.of(user));
        when(user.isMustChangePassword()).thenReturn(true);
    }

    private static void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ayesha.khan", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    private static MockHttpServletRequest api(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
