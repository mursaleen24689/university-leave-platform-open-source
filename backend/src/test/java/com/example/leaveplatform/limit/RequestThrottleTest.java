package com.example.leaveplatform.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RequestThrottleTest {

    private static final String ADDRESS = "203.0.113.7";
    private static final Instant START = Instant.parse("2026-11-10T09:00:00Z");
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final RequestThrottle throttle = new RequestThrottle(3, MINUTE, now::get);

    @Test
    void threeKnocksInOneMinuteAreLeftAlone() {
        for (int knock = 1; knock <= 3; knock++) {
            assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        }
    }

    @Test
    void theFourthKnockIsRefusedForTheRestOfThatMinute() {
        for (int knock = 1; knock <= 3; knock++) {
            throttle.refusalFor(ADDRESS);
        }
        now.set(START.plusSeconds(20));

        assertThat(throttle.refusalFor(ADDRESS)).hasValue(Duration.ofSeconds(40));
    }

    @Test
    void knockingWhileRefusedDoesNotPushTheWaitFurtherAway() {
        for (int knock = 1; knock <= 3; knock++) {
            throttle.refusalFor(ADDRESS);
        }

        Duration first = throttle.refusalFor(ADDRESS).orElseThrow();
        now.set(START.plusSeconds(30));
        Duration later = throttle.refusalFor(ADDRESS).orElseThrow();

        assertThat(first).isEqualTo(Duration.ofSeconds(60));
        assertThat(later).isEqualTo(Duration.ofSeconds(30));
        // A script refused fast must still reach the end of its wait; a count
        // that climbed on every knock would refuse this address forever.
        assertThat(later).isLessThan(first);
    }

    @Test
    void aMinuteAfterTheWindowOpenedTheAddressIsBack() {
        for (int knock = 1; knock <= 4; knock++) {
            throttle.refusalFor(ADDRESS);
        }
        now.set(START.plusSeconds(59));
        assertThat(throttle.refusalFor(ADDRESS)).isPresent();

        now.set(START.plusSeconds(61));
        assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        assertThat(throttle.refusalFor(ADDRESS)).isPresent();
    }

    @Test
    void oneAddressIsNotCountedAgainstAnother() {
        for (int knock = 1; knock <= 4; knock++) {
            throttle.refusalFor(ADDRESS);
        }

        assertThat(throttle.refusalFor("198.51.100.4")).isEmpty();
    }

    @Test
    void anAddressThatWasQuietStartsAFreshCount() {
        throttle.refusalFor(ADDRESS);
        now.set(START.plusSeconds(90));

        assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        assertThat(throttle.refusalFor(ADDRESS)).isEmpty();
        assertThat(throttle.refusalFor(ADDRESS)).isPresent();
    }

    @Test
    void theRoomToRememberIsCappedSoKnockingFromManyAddressesCannotFillTheServer() {
        RequestThrottle wide = new RequestThrottle(1, MINUTE, now::get);
        for (int address = 0; address <= RequestThrottle.MAX_KEYS; address++) {
            wide.refusalFor("10.0." + (address / 256) + "." + (address % 256));
        }

        assertThat(wide.trackedKeys()).isLessThanOrEqualTo(RequestThrottle.MAX_KEYS + 1);
    }

    @Test
    void closedWindowsAreTheFirstThingDroppedWhenTheRoomRunsOut() {
        RequestThrottle wide = new RequestThrottle(1, MINUTE, now::get);
        for (int address = 0; address <= RequestThrottle.MAX_KEYS; address++) {
            wide.refusalFor("10.0." + (address / 256) + "." + (address % 256));
        }
        now.set(START.plusSeconds(61));

        wide.refusalFor("203.0.113.99");

        // Every old window had closed by then, so the whole map is empty again
        // rather than full of counts that mean nothing.
        assertThat(wide.trackedKeys()).isOne();
    }

    @Test
    void aLimitThatWouldRefuseTheFirstKnockIsNotALimitAnyoneMeant() {
        assertThatThrownBy(() -> new RequestThrottle(0, MINUTE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestThrottle(3, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
