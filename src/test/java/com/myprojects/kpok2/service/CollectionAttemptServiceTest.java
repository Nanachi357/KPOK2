package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.CollectionAttempt;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.model.dto.ReviewAttemptResult;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.repository.CollectionAttemptRepository;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionAttemptServiceTest {

    private static final QuizTarget TARGET = QuizTarget.fromUrl(
            "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");

    @Test
    void createsAttemptWithInitialAndLatestCounts() {
        CollectionAttemptRepository repository = mock(CollectionAttemptRepository.class);
        NavigationSession session = session("456");
        ReviewAttemptResult result = new ReviewAttemptResult(List.of(
                new ReviewPageParsingResult("review-1", 50, 50, 12, null),
                new ReviewPageParsingResult(
                        "review-2", 50, 48, 2, "Parsed 48 of 50 discovered questions")));
        when(repository.findByQuizIdAndSourceAttemptId(173L, "456"))
                .thenReturn(Optional.empty());

        new CollectionAttemptService(repository).recordAttempt(
                session, List.of("review-1", "review-2"), result);

        ArgumentCaptor<CollectionAttempt> saved = ArgumentCaptor.forClass(CollectionAttempt.class);
        verify(repository).save(saved.capture());
        CollectionAttempt attempt = saved.getValue();
        assertEquals(173L, attempt.getQuizId());
        assertEquals("456", attempt.getSourceAttemptId());
        assertEquals(1, attempt.getCollectionRuns());
        assertEquals(100, attempt.getDiscoveredQuestions());
        assertEquals(98, attempt.getParsedQuestions());
        assertEquals(14, attempt.getInitialNewQuestions());
        assertEquals(14, attempt.getLatestNewQuestions());
        assertEquals(84, attempt.getLatestDuplicateQuestions());
        assertEquals(2, attempt.getRejectedQuestions());
        assertFalse(attempt.isComplete());
        assertTrue(attempt.getLastError().contains("Parsed 48 of 50"));
    }

    @Test
    void repeatedCollectionPreservesInitialNewCount() {
        CollectionAttemptRepository repository = mock(CollectionAttemptRepository.class);
        LocalDateTime firstCollectedAt = LocalDateTime.now().minusDays(1);
        CollectionAttempt existing = CollectionAttempt.builder()
                .id(9L)
                .quizId(173L)
                .sourceAttemptId("456")
                .firstCollectedAt(firstCollectedAt)
                .initialNewQuestions(14)
                .collectionRuns(1)
                .build();
        when(repository.findByQuizIdAndSourceAttemptId(173L, "456"))
                .thenReturn(Optional.of(existing));
        ReviewAttemptResult result = new ReviewAttemptResult(List.of(
                new ReviewPageParsingResult("review", 100, 100, 0, null)));

        new CollectionAttemptService(repository).recordAttempt(
                session("456"), List.of("review"), result);

        assertEquals(14, existing.getInitialNewQuestions());
        assertEquals(firstCollectedAt, existing.getFirstCollectedAt());
        assertEquals(0, existing.getLatestNewQuestions());
        assertEquals(100, existing.getLatestDuplicateQuestions());
        assertEquals(2, existing.getCollectionRuns());
        assertTrue(existing.isComplete());
        verify(repository).save(existing);
    }

    @Test
    void skipsSessionWithoutAttemptId() {
        CollectionAttemptRepository repository = mock(CollectionAttemptRepository.class);
        NavigationSession session = session(null);

        new CollectionAttemptService(repository).recordAttempt(
                session, List.of(), new ReviewAttemptResult(List.of()));

        verify(repository, never()).save(any());
    }

    private static NavigationSession session(String attemptId) {
        NavigationSession session = mock(NavigationSession.class);
        when(session.getQuizTarget()).thenReturn(TARGET);
        when(session.getAttemptId()).thenReturn(attemptId);
        return session;
    }
}
