package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class QuestionOriginService {

    private static final String UNDETERMINED_LANGUAGE = "und";

    private final QuestionOriginRepository repository;

    @Transactional
    public void recordOrigin(TestQuestion question, TestQuestion parsedQuestion) {
        if (question == null || question.getId() == null
                || parsedQuestion == null || parsedQuestion.getSourceQuizId() == null) {
            return;
        }

        repository.upsertOrigin(
                question.getId(),
                parsedQuestion.getSourceQuizId(),
                safe(parsedQuestion.getSourceUrl()),
                normalizeLanguage(parsedQuestion.getSourceLanguage()),
                LocalDateTime.now());
    }

    @Transactional
    public int backfillLegacyOrigins() {
        return repository.backfillLegacyOrigins();
    }

    static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return UNDETERMINED_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
