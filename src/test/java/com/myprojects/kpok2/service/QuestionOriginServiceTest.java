package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuestionOriginServiceTest {

    @Test
    void recordsNormalizedOriginForPersistedQuestion() {
        QuestionOriginRepository repository = mock(QuestionOriginRepository.class);
        QuestionOriginService service = new QuestionOriginService(repository);
        TestQuestion stored = TestQuestion.builder().id(42L).build();
        TestQuestion parsed = TestQuestion.builder()
                .sourceQuizId(173L)
                .sourceUrl(" https://test.testcentr.org.ua/mod/quiz/view.php?id=173 ")
                .sourceLanguage(" UK ")
                .build();

        LocalDateTime before = LocalDateTime.now();
        service.recordOrigin(stored, parsed);

        ArgumentCaptor<LocalDateTime> observedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).upsertOrigin(
                eq(42L),
                eq(173L),
                eq(
                        "https://test.testcentr.org.ua/mod/quiz/view.php?id=173"),
                eq("uk"),
                observedAt.capture());
        LocalDateTime now = LocalDateTime.now();
        assertFalse(observedAt.getValue().isBefore(before));
        assertTrue(observedAt.getValue().isBefore(now)
                || observedAt.getValue().isEqual(now));
    }

    @Test
    void usesUndeterminedLanguageWhenCatalogLanguageIsMissing() {
        assertEquals("und", QuestionOriginService.normalizeLanguage("  "));
        assertEquals("und", QuestionOriginService.normalizeLanguage(null));
    }

    @Test
    void skipsOriginWithoutPersistedQuestionOrQuiz() {
        QuestionOriginRepository repository = mock(QuestionOriginRepository.class);
        QuestionOriginService service = new QuestionOriginService(repository);

        service.recordOrigin(TestQuestion.builder().build(), TestQuestion.builder()
                .sourceQuizId(173L).build());
        service.recordOrigin(TestQuestion.builder().id(42L).build(), TestQuestion.builder().build());

        verify(repository, never()).upsertOrigin(any(), any(), any(), any(), any());
    }
}
