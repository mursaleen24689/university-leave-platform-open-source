package com.example.leaveplatform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.example.leaveplatform.identity.PasswordService;

/**
 * Session endpoints for API clients. The sign-in screen itself is the Vaadin
 * view at {@code /login}; login and logout are Spring Security filters. This
 * controller exposes the CSRF token, the signed-in profile, the security code
 * the sign-in screen shows and the self-service password change.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthQueryService profiles;
    private final PasswordService passwords;
    private final CaptchaService captcha;

    public AuthController(AuthQueryService profiles, PasswordService passwords, CaptchaService captcha) {
        this.profiles = profiles;
        this.passwords = passwords;
        this.captcha = captcha;
    }

    /**
     * The security code as a picture, for whoever is standing at the sign-in
     * screen. Fetching it issues a fresh answer into that visitor's own session,
     * so the code on the screen is always the code the server goes on to expect.
     * The browser is told to keep nothing: a cached picture would be a code the
     * server has already thrown away.
     *
     * <p>An address that asks faster than a person can click, or a browser that
     * has guessed its way into a wait, is answered with 429, a {@code Retry-After}
     * and a picture that says what to wait for instead of a code. That is the
     * limit's whole message, and the sign-in attempt it prevents never happens:
     * the refused visitor holds no answer the server would accept.
     */
    @GetMapping(value = "/captcha", produces = "image/svg+xml;charset=UTF-8")
    ResponseEntity<String> captcha(HttpSession session, HttpServletRequest request) {
        CaptchaService.Picture picture = captcha.newChallenge(session, request.getRemoteAddr());
        if (picture.isWaiting()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(picture.waitLeft().toSeconds()))
                    .body(picture.svg());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(picture.svg());
    }

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/me")
    MeResponse me(Authentication authentication) {
        return profiles.profile(authentication.getName());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        passwords.change(authentication.getName(), request.currentPassword(), request.newPassword());
    }
}
