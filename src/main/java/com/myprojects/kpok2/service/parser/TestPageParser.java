package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.exception.parser.ParsingErrorType;
import com.myprojects.kpok2.exception.parser.ParsingException;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestPageParser {
    private final WebDriver webDriver;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public List<ParsedTestQuestionDto> parse(String url) {
        log.debug("Starting parsing URL: {}", url);
        loadPage(url);
        return parseTestPage();
    }

    private void loadPage(String url) {
        try {
            log.info("Loading URL: {}", url);
            webDriver.get(url);
            logPageState("After initial page load");

            WebDriverWait wait = new WebDriverWait(webDriver, TIMEOUT);
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(TestParserConstants.QUESTION_SELECTOR)));

            logPageState("After waiting for questions");
        } catch (TimeoutException e) {
            log.error("Timeout while loading URL: {}", url, e);
            throw new ParsingException(
                    ParsingErrorType.TIMEOUT_ERROR,
                    String.format("Timeout while loading page %s: %s", url, e.getMessage()),
                    e
            );
        } catch (Exception e) {
            log.error("Failed to load URL: {}", url, e);
            throw new ParsingException(
                    ParsingErrorType.CONNECTION_ERROR,
                    String.format("Failed to load page %s: %s", url, e.getMessage()),
                    e
            );
        }
    }

    private List<ParsedTestQuestionDto> parseTestPage() {
        WebDriverWait wait = new WebDriverWait(webDriver, TIMEOUT);

        try {
            List<WebElement> questionElements = wait.until(ExpectedConditions
                    .presenceOfAllElementsLocatedBy(By.cssSelector(TestParserConstants.QUESTION_SELECTOR)));

            if (questionElements.isEmpty()) {
                logPageState("No questions found");
                throw new ParsingException(ParsingErrorType.NO_QUESTIONS, "No questions found on the page");
            }

            List<ParsedTestQuestionDto> questions = new ArrayList<>();
            for (WebElement questionElement : questionElements) {
                try {
                    questions.add(parseQuestion(questionElement, wait));
                } catch (Exception e) {
                    log.error("Failed to parse question", e);
                    logElementStructure(questionElement, "Failed question element");
                }
            }

            return questions;
        } catch (TimeoutException e) {
            logPageState("Timeout while parsing test page");
            throw new ParsingException(
                    ParsingErrorType.TIMEOUT_ERROR,
                    "Timeout while parsing test page: " + e.getMessage(),
                    e
            );
        } catch (Exception e) {
            logPageState("Failed to parse test page");
            throw new ParsingException(
                    ParsingErrorType.INVALID_PAGE_STRUCTURE,
                    "Failed to parse test page: " + e.getMessage(),
                    e
            );
        }
    }

    private ParsedTestQuestionDto parseQuestion(WebElement questionElement, WebDriverWait wait) {
        logElementStructure(questionElement, "Processing question element");

        String originalQuestionText = questionElement.getText();
        String normalizedQuestionText = normalizeText(originalQuestionText);

        try {
            WebElement answersContainer = wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(
                    questionElement,
                    By.cssSelector(TestParserConstants.ANSWERS_CONTAINER_SELECTOR)
            ));
            logElementStructure(answersContainer, "Found answers container");

            List<String> answers = answersContainer.findElements(
                            By.cssSelector(TestParserConstants.ANSWER_TEXT_SELECTOR))
                    .stream()
                    .map(WebElement::getText)
                    .collect(Collectors.toList());

            if (answers.isEmpty()) {
                log.warn("No answers found for question: {}", originalQuestionText);
                logElementStructure(answersContainer, "Empty answers container");
            }

            String originalCorrectAnswer = "";
            String normalizedCorrectAnswer = "";
            try {
                WebElement correctAnswerElement = wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(
                        questionElement,
                        By.cssSelector(TestParserConstants.CORRECT_ANSWER_SELECTOR)
                ));
                originalCorrectAnswer = correctAnswerElement.getText()
                        .replace(TestParserConstants.CORRECT_ANSWER_PREFIX, "");
                normalizedCorrectAnswer = normalizeText(originalCorrectAnswer);
            } catch (Exception e) {
                log.warn("Could not find correct answer for question: {}", originalQuestionText);
                logElementStructure(questionElement, "Question structure when correct answer not found");
            }

            return ParsedTestQuestionDto.builder()
                    .questionText(originalQuestionText)
                    .normalizedText(normalizedQuestionText)
                    .answers(answers)
                    .correctAnswer(originalCorrectAnswer)
                    .normalizedCorrectAnswer(normalizedCorrectAnswer)
                    .build();

        } catch (TimeoutException e) {
            throw new ParsingException(
                    ParsingErrorType.TIMEOUT_ERROR,
                    String.format("Timeout while parsing question: %s", e.getMessage()),
                    e
            );
        } catch (Exception e) {
            throw new ParsingException(
                    ParsingErrorType.MISSING_ELEMENT,
                    String.format("Failed to parse question: %s", e.getMessage()),
                    e
            );
        }
    }

    private void logPageState(String message) {
        try {
            log.debug("{}. Current URL: {}", message, webDriver.getCurrentUrl());
            log.debug("Page source:\n{}", webDriver.getPageSource());
        } catch (Exception e) {
            log.warn("Failed to log page state", e);
        }
    }

    private void logElementStructure(WebElement element, String message) {
        try {
            log.debug("{}. Element HTML:\n{}", message, element.getAttribute("outerHTML"));
        } catch (Exception e) {
            log.warn("Failed to log element structure", e);
        }
    }

    private String normalizeText(String text) {
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\n", " ")
                .replaceAll("\\r", "")
                .trim();
    }
}