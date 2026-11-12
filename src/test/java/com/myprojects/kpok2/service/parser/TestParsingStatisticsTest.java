package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ReviewAttemptResult;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestParsingStatisticsTest {

    @Test
    void recordsPrimaryReviewAttemptTotals() {
        TestParsingStatistics statistics = new TestParsingStatistics();
        ReviewAttemptResult result = new ReviewAttemptResult(List.of(
                new ReviewPageParsingResult("page-1", 50, 50, 7, null),
                new ReviewPageParsingResult(
                        "page-2", 50, 49, 1, "Parsed 49 of 50 discovered questions")));

        statistics.recordReviewAttempt(result);

        TestParsingStatistics.StatisticsData data = statistics.getCurrentStats();
        assertEquals(2, data.getProcessedCount());
        assertEquals(1, data.getSuccessCount());
        assertEquals(1, data.getFailedCount());
        assertEquals(8, data.getNewQuestionsCount());
    }
}
