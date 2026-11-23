package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.config.DebugProperties;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.QuestionAssetFetchService;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.service.catalog.QuizCatalogCache;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Component for parsing test pages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestParsingRunner {

    private final TestPageNavigator pageNavigator;
    private final TestQuestionParser questionParser;
    private final QuestionAssetFetchService questionAssetFetchService;
    private final TestQuestionService testQuestionService;
    private final DebugProperties debugProperties;
    private final TestParsingStatistics parsingStatistics;
    private final QuizCatalogCache quizCatalogCache;
    
    /**
     * Processes one review page and retains enough counts to verify that no
     * discovered Moodle question was silently discarded.
     */
    public ReviewPageParsingResult processReviewPage(String url, NavigationSession session) {
        log.info("Starting processing of URL: {}", url);

        int discoveredQuestions = 0;
        int parsedQuestions = 0;
        int newQuestionsCount = 0;

        try {
            WebDriver driver = session.getWebDriver();
            String username = session.getAccount().getUsername();
            
            // Navigate to the test page
            boolean success = pageNavigator.navigateToTestUrl(driver, url);
            if (!success) {
                log.error("Failed to navigate to URL: {}", url);
                return ReviewPageParsingResult.failure(url, "Could not load the review page");
            }

            Document document = Jsoup.parse(driver.getPageSource(), url);
            discoveredQuestions = questionParser.countQuestionContainers(document);
            if (discoveredQuestions == 0) {
                log.error("No Moodle question containers found at URL: {}", url);
                return ReviewPageParsingResult.failure(
                        url, "No Moodle question containers were found");
            }

            List<ParsedTestQuestionDto> questions = questionParser.parseQuestions(
                    document,
                    session.getQuizTarget().viewUrl(),
                    session.getQuizTarget().courseModuleId(),
                    catalogLanguageHint(session.getQuizTarget().courseModuleId()));
            questionAssetFetchService.prepareAssets(questions, driver);
            questions.forEach(question -> question.setSourceAttemptId(session.getAttemptId()));
            parsedQuestions = questions.size();

            // Save parsed questions
            TestQuestionService.SaveResult saveResult =
                    testQuestionService.saveQuestions(questions);
            newQuestionsCount = saveResult.newQuestionRecords();
            
            // Update account statistics
            parsingStatistics.registerAccountActivity(username, 1); // Increment page count for this account
            
            // Add session info
            parsingStatistics.addSessionInfo(username, 1, newQuestionsCount);
            
            // Save debug files if enabled
            if (debugProperties.isSaveFiles()) {
                pageNavigator.savePageSource(driver, url);
                pageNavigator.saveScreenshot(driver, url);
            }
            
            String errorMessage = parsedQuestions == discoveredQuestions
                    ? null
                    : "Parsed " + parsedQuestions + " of " + discoveredQuestions
                            + " discovered questions";
            if (errorMessage != null) {
                log.error("Incomplete review page {}: {}", url, errorMessage);
            }

            return new ReviewPageParsingResult(
                    url,
                    discoveredQuestions,
                    parsedQuestions,
                    newQuestionsCount,
                    errorMessage);
            
        } catch (Exception e) {
            log.error("Error processing URL {}: {}", url, e.getMessage(), e);
            return new ReviewPageParsingResult(
                    url,
                    discoveredQuestions,
                    parsedQuestions,
                    newQuestionsCount,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String catalogLanguageHint(long quizId) {
        var snapshot = quizCatalogCache.load();
        if (snapshot == null) {
            return null;
        }
        return snapshot.entries().stream()
                .filter(entry -> entry.courseModuleId() == quizId)
                .map(entry -> entry.language())
                .filter(language -> language != null && !language.isBlank())
                .findFirst()
                .orElse(null);
    }
}
