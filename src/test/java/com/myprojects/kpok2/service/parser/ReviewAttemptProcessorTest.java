package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ReviewAttemptResult;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.service.CollectionAttemptService;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewAttemptProcessorTest {

    @Test
    void retainsFailedPagesInAttemptSummary() {
        TestParsingRunner runner = mock(TestParsingRunner.class);
        TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        CollectionAttemptService attemptService = mock(CollectionAttemptService.class);
        NavigationSession session = mock(NavigationSession.class);
        when(runner.processReviewPage("page-1", session)).thenReturn(
                new ReviewPageParsingResult("page-1", 50, 50, 10, null));
        when(runner.processReviewPage("page-2", session)).thenReturn(
                new ReviewPageParsingResult(
                        "page-2", 50, 48, 2, "Parsed 48 of 50 discovered questions"));

        ReviewAttemptResult result = new ReviewAttemptProcessor(
                runner, statistics, attemptService).process(
                List.of("page-1", "page-2"), session);

        assertFalse(result.isComplete());
        assertEquals(1, result.successfulPages());
        assertEquals(1, result.failedPages());
        assertEquals(100, result.discoveredQuestions());
        assertEquals(98, result.parsedQuestions());
        assertEquals(2, result.rejectedQuestions());
        verify(statistics).recordReviewAttempt(result);
        verify(attemptService).recordAttempt(
                session, List.of("page-1", "page-2"), result);
    }

    @Test
    void recordsAttemptWhenNoReviewPagesWereResolved() {
        TestParsingRunner runner = mock(TestParsingRunner.class);
        TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        CollectionAttemptService attemptService = mock(CollectionAttemptService.class);
        NavigationSession session = mock(NavigationSession.class);

        ReviewAttemptResult result = new ReviewAttemptProcessor(
                runner, statistics, attemptService).process(List.of(), session);

        assertFalse(result.isComplete());
        assertEquals(0, result.pages().size());
        verify(statistics).recordReviewAttempt(result);
        verify(attemptService).recordAttempt(session, List.of(), result);
        verifyNoInteractions(runner);
    }
}
