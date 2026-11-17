package com.myprojects.kpok2.model.dto.export;

import com.myprojects.kpok2.model.QuestionType;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Immutable selection applied to a portable question export. */
public record QuestionExportFilter(
        Set<Long> quizIds,
        Set<String> languages,
        Set<QuestionType> questionTypes,
        boolean conflictsOnly,
        boolean includeAttempts,
        String query) {

    public QuestionExportFilter {
        TreeSet<Long> sortedQuizIds = new TreeSet<>();
        if (quizIds != null) {
            quizIds.stream().filter(java.util.Objects::nonNull)
                    .forEach(sortedQuizIds::add);
        }
        quizIds = Set.copyOf(sortedQuizIds);
        languages = normalizeLanguages(languages);
        Set<QuestionType> sortedTypes = new TreeSet<>(
                Comparator.comparing(QuestionType::name));
        if (questionTypes != null) {
            questionTypes.stream().filter(java.util.Objects::nonNull)
                    .forEach(sortedTypes::add);
        }
        questionTypes = Set.copyOf(sortedTypes);
        query = query == null ? "" : query.trim();
    }

    public static QuestionExportFilter all() {
        return new QuestionExportFilter(
                Set.of(), Set.of(), Set.of(), false, true, "");
    }

    public boolean hasScopeFilter() {
        return !quizIds.isEmpty() || !languages.isEmpty();
    }

    public boolean hasQuestionFilter() {
        return hasScopeFilter()
                || !questionTypes.isEmpty()
                || conflictsOnly
                || !query.isBlank();
    }

    public boolean hasNonQuizFilter() {
        return !languages.isEmpty()
                || !questionTypes.isEmpty()
                || conflictsOnly
                || !query.isBlank();
    }

    private static Set<String> normalizeLanguages(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .forEach(normalized::add);
        return Set.copyOf(normalized);
    }
}
