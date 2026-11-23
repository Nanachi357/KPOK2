package com.myprojects.kpok2.service.parser;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionRunProgressTest {

    @Test
    void estimatesRemainingTimeFromAggregateCompletedAttemptRate() {
        CollectionRunProgress progress = CollectionRunProgress.calculate(
                4,
                10,
                Duration.ofMinutes(2));

        assertEquals(2.0, progress.attemptsPerMinute(), 0.001);
        assertEquals(
                Duration.ofMinutes(3),
                progress.estimatedRemaining().orElseThrow());
    }

    @Test
    void doesNotClaimAnEtaBeforeTheFirstAttemptCompletes() {
        CollectionRunProgress progress = CollectionRunProgress.calculate(
                0,
                10,
                Duration.ofMinutes(2));

        assertEquals(0, progress.attemptsPerMinute());
        assertTrue(progress.estimatedRemaining().isEmpty());
    }

    @Test
    void unlimitedRunsReportThroughputButNeverAnEta() {
        CollectionRunProgress progress = CollectionRunProgress.calculate(
                3,
                0,
                Duration.ofMinutes(2));

        assertTrue(progress.unlimited());
        assertEquals(1.5, progress.attemptsPerMinute(), 0.001);
        assertTrue(progress.estimatedRemaining().isEmpty());
    }

    @Test
    void completedFiniteRunHasZeroRemainingTime() {
        CollectionRunProgress progress = CollectionRunProgress.calculate(
                10,
                10,
                Duration.ofMinutes(5));

        assertEquals(Duration.ZERO, progress.estimatedRemaining().orElseThrow());
    }
}
