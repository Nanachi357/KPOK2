package com.myprojects.kpok2.model.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewAttemptResultTest {

    @Test
    void summarizesCompleteAttempt() {
        ReviewAttemptResult result = new ReviewAttemptResult(List.of(
                new ReviewPageParsingResult("page-1", 50, 50, 12, null),
                new ReviewPageParsingResult("page-2", 50, 50, 3, null)));

        assertTrue(result.isComplete());
        assertEquals(2, result.successfulPages());
        assertEquals(0, result.failedPages());
        assertEquals(100, result.discoveredQuestions());
        assertEquals(100, result.parsedQuestions());
        assertEquals(15, result.newQuestions());
        assertEquals(85, result.duplicateQuestions());
        assertEquals(0, result.rejectedQuestions());
    }

    @Test
    void reportsPartialPageAsIncomplete() {
        ReviewAttemptResult result = new ReviewAttemptResult(List.of(
                new ReviewPageParsingResult("page-1", 50, 49, 4,
                        "Parsed 49 of 50 discovered questions")));

        assertFalse(result.isComplete());
        assertEquals(0, result.successfulPages());
        assertEquals(1, result.failedPages());
        assertEquals(1, result.rejectedQuestions());
    }
}
