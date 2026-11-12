package com.example.leaveplatform.limit;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * How many times one key - an address, as good as always - may knock on one door
 * inside one window of time, counted in this process and nowhere else.
 *
 * <p>Two shapes matter here. The window is fixed rather than sliding because a
 * visitor who is turned away has to be told how long to wait, and a sliding
 * count cannot name one; and the memory is capped because a map keyed by address
 * is itself a way to make this server run out of room - a stranger knocking from
 * a few thousand addresses needs one entry each. When the cap is reached the
 * windows that have already closed are dropped, and if that is not enough the
 * ones that opened earliest are. A visitor whose count is forgotten is served
 * again, which is the cheaper of the two failures: it costs the platform one
 * request, and it costs an attacker nothing they were not already paying.
 *
 * <p>The count stops climbing once the limit is reached. Knocking while refused
 * must not push the end of the window further away, or a script that is refused
 * fast enough is refused forever and the wait never runs out.
 */
public class RequestThrottle {

    /** Addresses remembered before any are dropped, so a botnet cannot grow this map. */
    static final int MAX_KEYS = 20_000;

    private final int allowedPerWindow;
    private final Duration window;
    private final Supplier<Instant> clock;
    private final Map<String, Count> open = new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();

    public RequestThrottle(int allowedPerWindow, Duration window) {
        this(allowedPerWindow, window, Instant::now);
    }

    RequestThrottle(int allowedPerWindow, Duration window, Supplier<Instant> clock) {
        if (allowedPerWindow < 1) {
            throw new IllegalArgumentException("A limit has to let at least one request through.");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("A limit has to be counted over a span of time.");
        }
        this.allowedPerWindow = allowedPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Records one knock and answers what to do about it: nothing when this key is
     * still inside its window, otherwise how long it has to wait before the next
     * knock is let through. The key is the caller's to name - an address here, an
     * address and a path together anywhere else - and keys never share a count.
     */
    public Optional<Duration> refusalFor(String key) {
        Instant now = clock.get();
        if (open.size() > MAX_KEYS) {
            dropClosedWindows(now);
        }
        Count count = open.compute(key, (ignored, previous) -> previous == null || previous.isClosedAt(now, window)
                ? new Count(now, 1)
                : new Count(previous.openedAt, Math.min(previous.knocks + 1, allowedPerWindow + 1)));
        return count.knocks <= allowedPerWindow
                ? Optional.empty()
                : Optional.of(Duration.between(now, count.openedAt.plus(window)));
    }

    /** How many addresses are being counted, for an operator watching the limit bite. */
    public int trackedKeys() {
        return open.size();
    }

    private void dropClosedWindows(Instant now) {
        synchronized (evictionLock) {
            if (open.size() <= MAX_KEYS) {
                return;
            }
            open.entrySet().removeIf(entry -> entry.getValue().isClosedAt(now, window));
            int surplus = open.size() - MAX_KEYS;
            if (surplus <= 0) {
                return;
            }
            // Still too many: the earliest windows go first, since a count that
            // opened longest ago is the one closest to expiring by itself anyway.
            List<String> oldest = open.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getValue().openedAt))
                    .limit(surplus)
                    .map(Map.Entry::getKey)
                    .toList();
            oldest.forEach(open::remove);
        }
    }

    private record Count(Instant openedAt, int knocks) {

        boolean isClosedAt(Instant now, Duration window) {
            return !openedAt.plus(window).isAfter(now);
        }
    }
}
