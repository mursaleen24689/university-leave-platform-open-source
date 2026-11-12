package com.example.leaveplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.example.leaveplatform.identity.Employee;

/**
 * Switching an account off has to reach the session it already has, not only the
 * sign-in screen it has not visited yet.
 */
class AccountStandingFilterTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUser user = mock(AppUser.class);
    private final Employee employee = mock(Employee.class);
    private final AccountStandingFilter filter = new AccountStandingFilter(users);

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void letsAnAccountThatIsStillEnabledThrough() throws Exception {
        given(true, true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void endsTheSessionOfAnAccountThatHasBeenDisabled() throws Exception {
        given(false, true);
        MockHttpServletRequest request = api("POST", "/api/v1/leave/1/approve");
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void endsTheSessionOfAPersonWhoHasLeftTheUniversity() throws Exception {
        // The account can still be enabled while the employment behind it has
        // ended; both have to agree before the session is allowed to continue.
        given(true, false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/reports/leave-register"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void endsTheSessionOfAnAccountThatNoLongerExists() throws Exception {
        signIn();
        when(users.findByUsername("ayesha.khan")).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/admin/accounts"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void refusesTheScreensAsWellAsTheApi() throws Exception {
        given(false, true);
        MockHttpServletRequest screen = new MockHttpServletRequest("GET", "/approvals");
        screen.setServletPath("/approvals");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(screen, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("no longer active");
    }

    @Test
    void leavesTheSignInAndSignOutDoorsOpen() throws Exception {
        given(false, true);

        for (String path : List.of(CaptchaEnforcementFilter.LOGIN_PATH, AccountStandingFilter.LOGOUT_PATH)) {
            MockHttpServletRequest request = api("POST", path);
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }
        verifyNoInteractions(users);
    }

    @Test
    void doesNotSignOutAColleagueWhosePasswordIsBeingGuessed() throws Exception {
        // Five wrong passwords at the sign-in screen lock the name, and anybody
        // can do that to a name they do not own. The lock refuses the next
        // sign-in; it must not throw a working session out.
        given(true, true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/leave"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(user, never()).isLocked(any());
    }

    @Test
    void leavesAnAnonymousVisitorToTheAuthorizationRules() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/api/v1/reports/verify"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    @Test
    void leavesASignedOutRequestAlone() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(api("GET", "/app.css"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    private void given(boolean enabled, boolean employed) {
        signIn();
        when(users.findByUsername("ayesha.khan")).thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(enabled);
        when(user.getEmployee()).thenReturn(employee);
        when(employee.isActive()).thenReturn(employed);
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