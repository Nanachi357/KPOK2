package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.QuestionType;

import java.util.Locale;

/** Optional filters applied to a canonical question-library page. */
public record QuestionLibraryFilter(
        String query,
        Long quizId,
        String language,
        QuestionType questionType,
        boolean conflictsOnly) {

    public QuestionLibraryFilter {
        query = query == null ? "" : query.trim();
        language = language == null || language.isBlank()
                ? ""
                : language.trim().toLowerCase(Locale.ROOT);
    }

    public static QuestionLibraryFilter search(String query) {
        return new QuestionLibraryFilter(query, null, "", null, false);
    }
}
