package com.example.leaveplatform.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import com.example.leaveplatform.auth.CaptchaService;
import com.example.leaveplatform.auth.TwoStepService;
import com.example.leaveplatform.identity.AppUserRepository;
import com.example.leaveplatform.ui.LoginView;

/**
 * Default-deny security for the Vaadin user interface and the JSON API.
 *
 * <p>Vaadin's configurer owns form login at {@code /login}, logout, the
 * static-resource and framework-internal request matchers, CSRF for the UI and
 * a final {@code anyRequest} rule that defaults to deny-all in Vaadin 25. The
 * authenticated default must therefore be declared through its
 * {@code anyRequest} hook, and custom permit rules must be registered before
 * it because that rule ends the list. The API answers 401 instead of
 * redirecting, so scripts and probes are never sent to an HTML login page.
 * See {@code docs/SECURITY.md}.
 */
@Configuration
public class SecurityConfig {

    static final String CSRF_URL = "/api/v1/auth/csrf";
    static final String CAPTCHA_URL = "/api/v1/auth/captcha";

    /**
     * The one read under {@code /reports/} that a person holding a printed form
     * is allowed to ask without an account of their own. It answers only what
     * the paper already carries, and only when the code printed beside the
     * number proves the paper came from this platform.
     */
    static final String VERIFY_URL = "/api/v1/reports/verify";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CaptchaService captcha,
            TwoStepService twoStep, AppUserRepository users) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Public brand assets: the stylesheet and logo must load on the sign-in
                // page before anyone is authenticated.
                .requestMatchers(HttpMethod.GET, "/app.css", "/logo.png", "/favicon.ico").permitAll()
                // The security code picture is shown before anyone is signed in,
                // and it is the only API an anonymous visitor is allowed to read.
                .requestMatchers(HttpMethod.GET, CAPTCHA_URL).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("CENTRAL_ADMIN")
                .requestMatchers("/api/v1/audit/**").hasAnyRole("CENTRAL_ADMIN", "AUDITOR")
                // A report covers other people's leave, so only the roles that
                // administer or head a department get this far; which rows come
                // back is narrowed again inside ReportService. The verification
                // read is named first because it is the one exception: a clerk
                // with a sheet of paper and no account has to be able to ask it.
                .requestMatchers(HttpMethod.GET, VERIFY_URL).permitAll()
                .requestMatchers("/api/v1/reports/**").hasAnyRole("CENTRAL_ADMIN", "HOD")
                .requestMatchers(HttpMethod.GET, CSRF_URL).permitAll());
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer
                .loginView(LoginView.class)
                .anyRequest(authorize -> authorize.authenticated()));
        http.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.pathPattern("/api/**")));
        http.addFilterAfter(new SessionAbsoluteExpiryFilter(), SecurityContextHolderFilter.class);
        // An account switched off since it signed in loses the session on its
        // next request, rather than at the next timeout.
        http.addFilterAfter(new AccountStandingFilter(users), SessionAbsoluteExpiryFilter.class);
        // The screens reroute an account on its issued temporary password to the
        // change page; this is the same rule for callers who never navigate.
        http.addFilterAfter(new PasswordChangeEnforcementFilter(users), AccountStandingFilter.class);
        // Before the password is looked at, so an attempt that has not answered
        // the security code never reaches the database or the password hash.
        http.addFilterBefore(new CaptchaEnforcementFilter(captcha), UsernamePasswordAuthenticationFilter.class);
        // And the authenticator code after that: still before the password is
        // compared, but after the security code has been answered, so the counts
        // on that code are what limit guessing at this one too.
        http.addFilterAfter(new TwoStepEnforcementFilter(twoStep), CaptchaEnforcementFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
