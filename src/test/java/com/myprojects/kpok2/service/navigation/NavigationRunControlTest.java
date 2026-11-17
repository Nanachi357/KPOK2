package com.myprojects.kpok2.service.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationRunControlTest {

    @Test
    void reservesNoMoreThanTheSharedFiniteTarget() {
        NavigationRunControl control = new NavigationRunControl();
        assertTrue(control.beginRun(2));

        assertTrue(control.tryReserveIteration());
        assertTrue(control.tryReserveIteration());
        assertFalse(control.tryReserveIteration());

        control.finishIteration(true);
        control.finishIteration(true);

        assertEquals(2, control.getCompletedIterations());
        assertFalse(control.tryReserveIteration());
        assertFalse(control.shouldPrepareAnotherIteration());
    }

    @Test
    void failedIterationReleasesItsReservationForRetry() {
        NavigationRunControl control = new NavigationRunControl();
        control.beginRun(1);

        assertTrue(control.tryReserveIteration());
        control.finishIteration(false);

        assertEquals(0, control.getCompletedIterations());
        assertTrue(control.tryReserveIteration());
    }

    @Test
    void gracefulStopAllowsReservedWorkButRejectsNewWork() {
        NavigationRunControl control = new NavigationRunControl();
        control.beginRun(0);
        assertTrue(control.tryReserveIteration());

        assertTrue(control.requestStopAfterCurrent());
        assertTrue(control.isRunning());
        assertTrue(control.isStopAfterCurrentRequested());
        assertFalse(control.tryReserveIteration());
        assertFalse(control.shouldPrepareAnotherIteration());

        control.finishIteration(true);
        assertEquals(1, control.getCompletedIterations());
        assertEquals(0, control.getInFlightIterations());
    }

    @Test
    void immediateStopRejectsNewWorkAndAllowsNextRun() {
        NavigationRunControl control = new NavigationRunControl();
        control.beginRun(0);
        assertTrue(control.tryReserveIteration());

        assertTrue(control.stopNow());
        assertTrue(control.isRunning());
        assertTrue(control.isImmediateStopRequested());
        assertFalse(control.tryReserveIteration());
        assertFalse(control.beginRun(3));

        control.finishIteration(false);
        control.markStopped();
        assertFalse(control.isRunning());
        assertTrue(control.beginRun(3));
        assertFalse(control.isStopAfterCurrentRequested());
        assertFalse(control.isImmediateStopRequested());
    }
}
