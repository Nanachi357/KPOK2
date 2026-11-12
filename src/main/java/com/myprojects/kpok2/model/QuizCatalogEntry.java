package com.myprojects.kpok2.model;

import java.util.Locale;

/**
 * A quiz discovered in the authenticated TestCenter course catalog.
 */
public record QuizCatalogEntry(
        long courseModuleId,
        String title,
        String section,
        String krokLevel,
        String specialty,
        String language,
        String url) {

    public QuizTarget toQuizTarget() {
        return QuizTarget.fromUrl(url);
    }

    public String displayName() {
        StringBuilder label = new StringBuilder();
        if (krokLevel != null && !krokLevel.isBlank()) {
            label.append(krokLevel).append(" — ");
        }
        label.append(title);
        if (language != null && !language.isBlank()) {
            label.append(" [").append(language.toUpperCase(Locale.ROOT)).append(']');
        }
        return label.toString();
    }

    public String searchableText() {
        return String.join(" ",
                safe(title), safe(section), safe(krokLevel), safe(specialty), safe(language), url)
                .toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
