package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.exception.parser.ParsingErrorType;
import com.myprojects.kpok2.exception.parser.ParsingException;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
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

            String currentUrl = webDriver.getCurrentUrl();
            log.info("Current URL: {}", currentUrl);

            WebDriverWait wait = new WebDriverWait(webDriver, TIMEOUT);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TestParserConstants.QUESTION_SELECTOR)));
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
        List<WebElement> questionElements = webDriver.findElements(By.cssSelector(TestParserConstants.QUESTION_SELECTOR));

        if (questionElements.isEmpty()) {
            log.warn("No questions found in document");
            throw new ParsingException(ParsingErrorType.NO_QUESTIONS, "No questions found on the page");
        }

        List<ParsedTestQuestionDto> questions = new ArrayList<>();
        for (WebElement questionElement : questionElements) {
            questions.add(parseQuestion(questionElement));
        }

        return questions;
    }

    private ParsedTestQuestionDto parseQuestion(WebElement questionElement) {
        String originalQuestionText = questionElement.getText();
        String normalizedQuestionText = normalizeText(originalQuestionText);

        WebElement answersContainer = questionElement.findElement(By.cssSelector(TestParserConstants.ANSWERS_CONTAINER_SELECTOR));

        List<String> answers = answersContainer.findElements(By.cssSelector(TestParserConstants.ANSWER_TEXT_SELECTOR))
                .stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());

        String originalCorrectAnswer = "";
        String normalizedCorrectAnswer = "";
        try {
            WebElement correctAnswerElement = questionElement.findElement(By.cssSelector(TestParserConstants.CORRECT_ANSWER_SELECTOR));
            originalCorrectAnswer = correctAnswerElement.getText().replace("Правильна відповідь: ", "");
            normalizedCorrectAnswer = normalizeText(originalCorrectAnswer);
        } catch (Exception e) {
            log.warn("Could not find correct answer for question: {}", originalQuestionText);
        }

        return ParsedTestQuestionDto.builder()
                .questionText(originalQuestionText)
                .normalizedText(normalizedQuestionText)
                .answers(answers)
                .correctAnswer(originalCorrectAnswer)
                .normalizedCorrectAnswer(normalizedCorrectAnswer)
                .build();
    }

    private String normalizeText(String text) {
        return text.trim()
                .replaceAll("\\s+", " ")  // Replace multiple spaces with single space
                .replaceAll("\\n", " ")   // Replace newlines with space
                .replaceAll("\\r", "")    // Remove carriage returns
                .trim();                  // Final trim to remove any leading/trailing spaces
    }
}