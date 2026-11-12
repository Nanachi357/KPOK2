package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.config.DebugProperties;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.service.navigation.AccountCredentials;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import com.myprojects.kpok2.service.navigation.NavigationSessionFactory;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestParsingRunnerTest {

    private static final String REVIEW_URL =
            "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=456&cmid=173&showall=1";
    private static final QuizTarget TARGET = QuizTarget.fromUrl(
            "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");

    @Test
    void reportsCompletePageWithNewAndDuplicateQuestions() {
        Fixture fixture = fixture(2, 2, 1);

        ReviewPageParsingResult result = fixture.runner.processReviewPage(
                REVIEW_URL, fixture.session);

        assertTrue(result.isComplete());
        assertEquals(2, result.discoveredQuestions());
        assertEquals(2, result.parsedQuestions());
        assertEquals(1, result.newQuestions());
        assertEquals(1, result.duplicateQuestions());
        verify(fixture.statistics).registerAccountActivity("student", 1);
        verify(fixture.statistics).addSessionInfo("student", 1, 1);
    }

    @Test
    void reportsRejectedQuestionInsteadOfSilentlySucceeding() {
        Fixture fixture = fixture(2, 1, 1);

        ReviewPageParsingResult result = fixture.runner.processReviewPage(
                REVIEW_URL, fixture.session);

        assertFalse(result.isComplete());
        assertEquals(1, result.rejectedQuestions());
        assertEquals("Parsed 1 of 2 discovered questions", result.errorMessage());
    }

    private Fixture fixture(int discovered, int parsed, int saved) {
        NavigationSessionFactory sessionFactory = mock(NavigationSessionFactory.class);
        TestPageNavigator pageNavigator = mock(TestPageNavigator.class);
        TestQuestionParser questionParser = mock(TestQuestionParser.class);
        TestQuestionService questionService = mock(TestQuestionService.class);
        TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        DebugProperties debugProperties = new DebugProperties();
        NavigationSession session = mock(NavigationSession.class);
        WebDriver driver = mock(WebDriver.class);

        when(session.getWebDriver()).thenReturn(driver);
        when(session.getAccount()).thenReturn(new AccountCredentials("student", "secret"));
        when(session.getQuizTarget()).thenReturn(TARGET);
        when(pageNavigator.navigateToTestUrl(driver, REVIEW_URL)).thenReturn(true);
        when(driver.getPageSource()).thenReturn(reviewHtml(discovered));

        List<ParsedTestQuestionDto> parsedQuestions = java.util.stream.IntStream.range(0, parsed)
                .mapToObj(index -> mock(ParsedTestQuestionDto.class))
                .toList();
        when(questionParser.parseQuestions(
                any(Document.class), eq(TARGET.viewUrl()), eq(173L)))
                .thenReturn(parsedQuestions);
        List<TestQuestion> savedQuestions = java.util.stream.IntStream.range(0, saved)
                .mapToObj(index -> mock(TestQuestion.class))
                .toList();
        when(questionService.saveUniqueQuestions(parsedQuestions)).thenReturn(savedQuestions);

        TestParsingRunner runner = new TestParsingRunner(
                sessionFactory,
                pageNavigator,
                questionParser,
                questionService,
                debugProperties,
                statistics);
        return new Fixture(runner, session, statistics);
    }

    private String reviewHtml(int questionCount) {
        StringBuilder html = new StringBuilder("<html><body><main id='page-content'>");
        for (int index = 0; index < questionCount; index++) {
            html.append("<div class='que' id='question-")
                    .append(index)
                    .append("'><div class='qtext'>Question</div></div>");
        }
        return html.append("</main></body></html>").toString();
    }

    private record Fixture(
            TestParsingRunner runner,
            NavigationSession session,
            TestParsingStatistics statistics) {
    }
}
