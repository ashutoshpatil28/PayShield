package com.payshield.auth;

import com.payshield.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple sliding-window rate limiter for authentication endpoints.
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 20;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public void checkAllowed(String key) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
        Deque<Instant> timestamps = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS_PER_MINUTE) {
                throw new DomainException(
                        "RATE_LIMIT_EXCEEDED",
                        "Too many authentication attempts. Please wait before retrying.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            timestamps.addLast(now);
        }
    }
}
