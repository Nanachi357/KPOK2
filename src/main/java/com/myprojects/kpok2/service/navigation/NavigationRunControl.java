package com.myprojects.kpok2.service.navigation;

import org.springframework.stereotype.Component;

/**
 * Coordinates iteration reservations across all navigation workers.
 *
 * <p>A reservation prevents parallel workers from exceeding a finite target.
 * A graceful stop rejects new reservations while allowing already reserved
 * iterations to finish.</p>
 */
@Component
public class NavigationRunControl {
    private boolean running;
    private boolean stopAfterCurrentRequested;
    private int targetIterations;
    private int completedIterations;
    private int inFlightIterations;

    public synchronized boolean beginRun(int targetIterations) {
        if (running) {
            return false;
        }

        this.running = true;
        this.stopAfterCurrentRequested = false;
        this.targetIterations = targetIterations;
        this.completedIterations = 0;
        this.inFlightIterations = 0;
        return true;
    }

    public synchronized boolean tryReserveIteration() {
        if (!running || stopAfterCurrentRequested) {
            return false;
        }
        if (targetIterations > 0
                && completedIterations + inFlightIterations >= targetIterations) {
            return false;
        }

        inFlightIterations++;
        return true;
    }

    public synchronized void finishIteration(boolean successful) {
        if (inFlightIterations <= 0) {
            throw new IllegalStateException("No navigation iteration is currently reserved");
        }

        inFlightIterations--;
        if (successful) {
            completedIterations++;
        }
    }

    public synchronized boolean requestStopAfterCurrent() {
        if (!running) {
            return false;
        }
        stopAfterCurrentRequested = true;
        return true;
    }

    public synchronized boolean stopNow() {
        if (!running) {
            return false;
        }
        running = false;
        stopAfterCurrentRequested = true;
        return true;
    }

    public synchronized void markStopped() {
        running = false;
        inFlightIterations = 0;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isStopAfterCurrentRequested() {
        return stopAfterCurrentRequested;
    }

    public synchronized boolean shouldPrepareAnotherIteration() {
        return running
                && !stopAfterCurrentRequested
                && (targetIterations <= 0
                || completedIterations + inFlightIterations < targetIterations);
    }

    synchronized int getCompletedIterations() {
        return completedIterations;
    }

    synchronized int getInFlightIterations() {
        return inFlightIterations;
    }
}
