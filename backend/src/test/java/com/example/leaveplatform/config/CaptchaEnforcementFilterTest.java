package com.example.leaveplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import com.example.leaveplatform.auth.CaptchaService;
import com.example.leaveplatform.limit.RequestThrottle;

class CaptchaEnforcementFilterTest {

    private static final Pattern GLYPH = Pattern.compile("<text[^>]*>([^<]+)</text>");
    private static final String ADDRESS = "203.0.113.7";

    private final CaptchaService captcha = new CaptchaService(
            new RequestThrottle(100_000, Duration.ofMinutes(1)), 40, 3, 120);
    private final CaptchaEnforcementFilter filter = new CaptchaEnforcementFilter(captcha);

    @Test
    void letsASignInWithTheCodeInThePictureThrough() throws Exception {
        MockHttpServletRequest request = signIn();
        request.addParameter(CaptchaService.CODE_PARAMETER, showCode(request));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void refusesASignInThatLeftTheCodeEmpty() throws Exception {
        MockHttpServletRequest request = signIn();
        showCode(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/login?" + CaptchaService.REASON_PARAMETER + "=" + CaptchaService.CODE_MISSING);
    }

    @Test
    void refusesASignInThatGuessedTheCodeWrong() throws Exception {
        MockHttpServletRequest request = signIn();
        showCode(request);
        request.addParameter(CaptchaService.CODE_PARAMETER, "QQQQQQ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl()).endsWith("=" + CaptchaService.CODE_WRONG);
    }

    @Test
    void refusesASignInFromASessionThatNeverSawAPicture() throws Exception {
        MockHttpServletRequest request = signIn();
        request.addParameter(CaptchaService.CODE_PARAMETER, "ABC123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl()).endsWith("=" + CaptchaService.CODE_MISSING);
    }

    @Test
    void refusesASignInFromNoSessionAtAll() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath(CaptchaEnforcementFilter.LOGIN_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getRedirectedUrl()).endsWith("=" + CaptchaService.CODE_MISSING);
    }

    @Test
    void onePictureBuysOneSignInAttempt() throws Exception {
        MockHttpServletRequest request = signIn();
        String code = showCode(request);
        request.addParameter(CaptchaService.CODE_PARAMETER, code);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // The same code sent again, as a script would, buys nothing.
        MockHttpServletResponse second = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(request, second, secondChain);

        assertThat(secondChain.getRequest()).isNull();
        assertThat(second.getRedirectedUrl()).endsWith("=" + CaptchaService.CODE_MISSING);
    }

    @Test
    void aBrowserThatGuessedThreeCodesIsTurnedAwayWhileItWaits() throws Exception {
        MockHttpServletRequest request = signIn();
        String code = showCode(request);
        for (int guess = 1; guess <= 3; guess++) {
            request.setParameter(CaptchaService.CODE_PARAMETER, "QQQQQQ");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        // The right code is on the screen and would have passed a minute ago. The
        // wait answers first, so three bad guesses cannot be followed by a
        // hundred good ones against the same session.
        request.setParameter(CaptchaService.CODE_PARAMETER, code);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl()).endsWith("=" + CaptchaService.CODE_WAIT);
    }

    @Test
    void theAnswerTravelsWithTheSessionThatWasShownIt() throws Exception {
        MockHttpServletRequest shownTo = signIn();
        String code = showCode(shownTo);
        shownTo.addParameter(CaptchaService.CODE_PARAMETER, code);

        // The same code carried by a different session is refused - the answer
        // lives in the visitor it was drawn for, so reading it off somebody
        // else's screen buys nothing - and it is still there to be spent by the
        // person it was drawn for.
        MockHttpServletRequest fromSomebodyElse = signIn();
        fromSomebodyElse.setSession(new MockHttpSession());
        fromSomebodyElse.addParameter(CaptchaService.CODE_PARAMETER, code);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(fromSomebodyElse, response, chain);

        MockFilterChain theVisitorsOwn = new MockFilterChain();
        filter.doFilter(shownTo, new MockHttpServletResponse(), theVisitorsOwn);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getRedirectedUrl()).endsWith("=" + CaptchaService.CODE_MISSING);
        assertThat(theVisitorsOwn.getRequest()).isNotNull();
    }

    @Test
    void leavesEverythingThatIsNotASignInAttemptAlone() throws Exception {
        MockHttpServletRequest openThePage = new MockHttpServletRequest("GET", "/login");
        openThePage.setServletPath("/login");
        MockHttpServletRequest saveSomething = new MockHttpServletRequest("POST", "/api/v1/leave/applications");
        saveSomething.setServletPath("/api/v1/leave/applications");

        for (MockHttpServletRequest request : List.of(openThePage, saveSomething)) {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }
    }

    private static MockHttpServletRequest signIn() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath(CaptchaEnforcementFilter.LOGIN_PATH);
        return request;
    }

    /**
     * Draws a picture for this attempt and hands back its code, read out of the
     * picture the way a person reads it - which is the point of the test: what
     * is on the screen is what the server goes on to expect.
     */
    private String showCode(MockHttpServletRequest request) {
        Matcher glyphs = GLYPH.matcher(captcha.newChallenge(request.getSession(), ADDRESS).svg());
        StringBuilder code = new StringBuilder();
        while (glyphs.find()) {
            code.append(glyphs.group(1));
        }
        assertThat(code.toString()).hasSize(6);
        return code.toString();
    }
}
