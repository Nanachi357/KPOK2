package com.myprojects.kpok2.service.navigation;

/** Classifies collection settings that deserve an explicit user warning. */
public record CollectionRunAdvisory(
        Kind kind,
        int attempts,
        int threads,
        long parallelRounds) {

    public static final int LARGE_RUN_THRESHOLD = 100;

    public static CollectionRunAdvisory assess(int attempts, int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Thread count must be positive");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempt count cannot be negative");
        }
        if (attempts == 0) {
            return new CollectionRunAdvisory(Kind.UNLIMITED, attempts, threads, 0);
        }

        long rounds = ((long) attempts + threads - 1) / threads;
        Kind kind = attempts >= LARGE_RUN_THRESHOLD ? Kind.LARGE : Kind.NONE;
        return new CollectionRunAdvisory(kind, attempts, threads, rounds);
    }

    public boolean requiresWarning() {
        return kind != Kind.NONE;
    }

    public enum Kind {
        NONE,
        LARGE,
        UNLIMITED
    }
}
