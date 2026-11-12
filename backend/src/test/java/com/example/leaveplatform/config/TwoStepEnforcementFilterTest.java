package com.example.leaveplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.example.leaveplatform.auth.TwoStepService;

/**
 * Where the authenticator code sits in a sign-in: after the security code in the
 * picture and before anybody looks at the password, answered with the same page
 * a wrong password gets.
 */
class TwoStepEnforcementFilterTest {

    private final TwoStepService twoStep = mock(TwoStepService.class);
    private final TwoStepEnforcementFilter filter = new TwoStepEnforcementFilter(twoStep);

    @Test
    void letsAnAccountWithoutASecondStepThroughUnchanged() throws Exception {
        given(TwoStepService.CodeResult.NOT_ENROLLED);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(signInWith(null), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsAMatchingCodeThroughToThePassword() throws Exception {
        given(TwoStepService.CodeResult.ACCEPTED);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(signInWith("123456"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        // Passing through is all this filter does with a good code. It grants
        // nothing, so a correct number with a wrong password still fails.
        verify(twoStep).checkSignInCode("ayesha.khan", "123456");
    }

    @Test
    void refusesAWrongCodeWithoutSayingWhichHalfFailed() throws Exception {
        assertRefused(TwoStepService.CodeResult.REFUSED);
    }

    @Test
    void refusesASeedThePlatformCannotOpen() throws Exception {
        assertRefused(TwoStepService.CodeResult.SEED_UNREADABLE);
    }

    @Test
    void readsTheNumberFromTheFieldTheSignInScreenPosts() throws Exception {
        // The name is the contract between the Vaadin screen, this filter and the
        // live server; a rename that missed one of them would silently turn
        // two-step back off, because an empty parameter is simply not checked.
        assertThat(TwoStepService.CODE_PARAMETER).isEqualTo("otp");
        assertThat(TwoStepEnforcementFilter.USERNAME_PARAMETER).isEqualTo("username");

        given(TwoStepService.CodeResult.ACCEPTED);
        filter.doFilter(signInWith("654321"), new MockHttpServletResponse(), new MockFilterChain());

        verify(twoStep).checkSignInCode("ayesha.khan", "654321");
    }

    @Test
    void leavesEverythingThatIsNotASignInAttemptAlone() throws Exception {
        MockHttpServletRequest openThePage = new MockHttpServletRequest("GET", "/login");
        openThePage.setServletPath(CaptchaEnforcementFilter.LOGIN_PATH);
        MockHttpServletRequest twoStepScreen = new MockHttpServletRequest("POST", "/api/v1/auth/two-step/off");
        twoStepScreen.setServletPath("/api/v1/auth/two-step/off");

        for (MockHttpServletRequest request : List.of(openThePage, twoStepScreen)) {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }
        verifyNoInteractions(twoStep);
    }

    @Test
    void sendsTheBrowserToTheSignInPageUnderItsOwnAddress() throws Exception {
        given(TwoStepService.CodeResult.REFUSED);
        MockHttpServletRequest request = signInWith("000000");
        request.setContextPath("/leave");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isEqualTo("/leave/login?error");
    }

    private void assertRefused(TwoStepService.CodeResult result) throws Exception {
        given(result);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(signInWith("000000"), response, chain);

        // The identical page a wrong password produces: no "wrong code", nothing
        // that tells a guesser the password they paired it with was the right one.
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(response.getErrorMessage()).isNull();
    }

    private void given(TwoStepService.CodeResult result) {
        when(twoStep.checkSignInCode("ayesha.khan", "000000")).thenReturn(result);
        when(twoStep.checkSignInCode("ayesha.khan", "123456")).thenReturn(result);
        when(twoStep.checkSignInCode("ayesha.khan", null)).thenReturn(result);
    }

    private static MockHttpServletRequest signInWith(String code) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath(CaptchaEnforcementFilter.LOGIN_PATH);
        request.setParameter(TwoStepEnforcementFilter.USERNAME_PARAMETER, "ayesha.khan");
        if (code != null) {
            request.setParameter(TwoStepService.CODE_PARAMETER, code);
        }
        return request;
    }
}
