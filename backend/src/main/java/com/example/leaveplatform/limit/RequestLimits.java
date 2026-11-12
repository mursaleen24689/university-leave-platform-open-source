package com.example.leaveplatform.limit;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The counts kept against the two reads that answer without a signed-in
 * account, so neither one becomes a free service a script can call.
 *
 * <p>Both are per minute because a minute is the shortest span that a person
 * pressing a button cannot exhaust and a script notices within seconds. Both
 * numbers are deliberately far above any human use: the point is not to slow a
 * visitor down but to put a ceiling on how fast one address can create sign-in
 * attempts or database reads. Everything else about a visitor is counted against
 * their own session, where a bot has to start again from nothing to make
 * progress - see {@code auth/CaptchaService}.
 *
 * <p>The counts live in this process, so an instance behind a load balancer
 * limits its own share. A campus that leaves the university network through one
 * address needs the numbers raised, not the limit removed; they are properties
 * for exactly that.
 */
@Configuration
public class RequestLimits {

    /**
     * Code pictures one address may be shown in a minute. Each one is the price
     * of one sign-in attempt, so this is the ceiling on how fast one address can
     * try passwords - and on how fast it can fill the server with sessions.
     */
    @Bean
    public RequestThrottle captchaPictureLimit(
            @Value("${app.sign-in.code-pictures-per-address-per-minute:120}") int perMinute) {
        return new RequestThrottle(perMinute, Duration.ofMinutes(1));
    }

    /**
     * Printed forms one address may check in a minute. The check is open to
     * anybody holding paper, so a scanner in a crowded office is normal traffic;
     * what is not normal is a machine working through numbers, and a guess in the
     * code space is worthless at any rate the limit allows.
     */
    @Bean
    public RequestThrottle verifyLimit(
            @Value("${app.verify.checks-per-address-per-minute:60}") int perMinute) {
        return new RequestThrottle(perMinute, Duration.ofMinutes(1));
    }
}
