package com.myprojects.kpok2.service.parser;

import java.time.Duration;
import java.util.Optional;

/** Immutable wall-clock progress and throughput estimate for one collection run. */
public record CollectionRunProgress(
        int completedAttempts,
        int targetAttempts,
        Duration elapsed,
        double attemptsPerMinute,
        Optional<Duration> estimatedRemaining) {

    public CollectionRunProgress {
        completedAttempts = Math.max(0, completedAttempts);
        elapsed = elapsed == null || elapsed.isNegative() ? Duration.ZERO : elapsed;
        attemptsPerMinute = Math.max(0, attemptsPerMinute);
        estimatedRemaining = estimatedRemaining == null
                ? Optional.empty()
                : estimatedRemaining;
    }

    public static CollectionRunProgress calculate(
            int completedAttempts,
            int targetAttempts,
            Duration elapsed) {
        int completed = Math.max(0, completedAttempts);
        Duration safeElapsed = elapsed == null || elapsed.isNegative()
                ? Duration.ZERO
                : elapsed;
        long elapsedMillis = safeElapsed.toMillis();
        if (completed == 0 || elapsedMillis < 1_000) {
            return new CollectionRunProgress(
                    completed,
                    targetAttempts,
                    safeElapsed,
                    0,
                    Optional.empty());
        }

        double attemptsPerMinute = completed * 60_000.0 / elapsedMillis;
        Optional<Duration> remaining = Optional.empty();
        if (targetAttempts > 0) {
            int attemptsLeft = Math.max(0, targetAttempts - completed);
            long remainingMillis = Math.round(
                    elapsedMillis * (attemptsLeft / (double) completed));
            remaining = Optional.of(Duration.ofMillis(remainingMillis));
        }
        return new CollectionRunProgress(
                completed,
                targetAttempts,
                safeElapsed,
                attemptsPerMinute,
                remaining);
    }

    public boolean unlimited() {
        return targetAttempts <= 0;
    }
}
