package com.myprojects.kpok2.model;

import java.util.List;

/**
 * Locally cached catalog plus the time it was successfully refreshed.
 */
public record QuizCatalogSnapshot(String updatedAt, List<QuizCatalogEntry> entries) {

    public QuizCatalogSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static QuizCatalogSnapshot empty() {
        return new QuizCatalogSnapshot(null, List.of());
    }
}
