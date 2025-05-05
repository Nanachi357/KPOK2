package com.myprojects.kpok2.config;

import lombok.Getter;

@Getter
public enum TimeoutPreset {
    DEFAULT("Standard (fast internet)", 5, 5, 3),
    MEDIUM("Medium (unstable internet)", 10, 10, 6),
    SLOW("Slow (weak internet)", 15, 15, 9);

    private final String description;
    private final int pageLoadTimeoutSeconds;
    private final int navigationTimeoutSeconds;
    private final int elementTimeoutSeconds;

    TimeoutPreset(String description, int pageLoadTimeoutSeconds, int navigationTimeoutSeconds, int elementTimeoutSeconds) {
        this.description = description;
        this.pageLoadTimeoutSeconds = pageLoadTimeoutSeconds;
        this.navigationTimeoutSeconds = navigationTimeoutSeconds;
        this.elementTimeoutSeconds = elementTimeoutSeconds;
    }
} 