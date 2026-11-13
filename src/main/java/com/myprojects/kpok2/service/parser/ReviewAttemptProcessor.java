package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ReviewAttemptResult;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.service.CollectionAttemptService;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Processes all resolved review pages and produces one completeness result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAttemptProcessor {

    private final TestParsingRunner parsingRunner;
    private final TestParsingStatistics parsingStatistics;
    private final CollectionAttemptService collectionAttemptService;

    public ReviewAttemptResult process(List<String> reviewUrls, NavigationSession session) {
        if (reviewUrls == null || reviewUrls.isEmpty()) {
            log.error("No review pages were resolved for the completed attempt");
            ReviewAttemptResult result = new ReviewAttemptResult(List.of());
            parsingStatistics.recordReviewAttempt(result);
            collectionAttemptService.recordAttempt(session, reviewUrls, result);
            return result;
        }

        List<ReviewPageParsingResult> pageResults = new ArrayList<>();
        for (String reviewUrl : reviewUrls) {
            pageResults.add(parsingRunner.processReviewPage(reviewUrl, session));
        }

        ReviewAttemptResult result = new ReviewAttemptResult(pageResults);
        parsingStatistics.recordReviewAttempt(result);
        collectionAttemptService.recordAttempt(session, reviewUrls, result);
        log.info(
                "Review attempt summary: pages={}/{}, discovered={}, parsed={}, new={}, "
                        + "duplicates={}, rejected={}, complete={}",
                result.successfulPages(),
                result.pages().size(),
                result.discoveredQuestions(),
                result.parsedQuestions(),
                result.newQuestions(),
                result.duplicateQuestions(),
                result.rejectedQuestions(),
                result.isComplete());
        return result;
    }
}
