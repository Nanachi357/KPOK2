package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.exception.parser.ParsingErrorType;
import com.myprojects.kpok2.exception.parser.ParsingException;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TestPageParser {
    private static final int TIMEOUT = 10000; // 10 seconds

    public List<ParsedTestQuestionDto> parse(String url) {
        log.debug("Starting parsing URL: {}", url);
        Document doc = getPage(url);
        return parseTestPage(doc);
    }

    private Document getPage(String url) {
        try {
            return Jsoup.connect(url)
                    .timeout(TIMEOUT)
                    .get();
        } catch (SocketTimeoutException e) {
            log.error("Timeout while accessing URL: {}", url, e);
            throw new ParsingException(
                    ParsingErrorType.TIMEOUT_ERROR,
                    String.format("Timeout accessing page %s", url),
                    e
            );
        } catch (IOException e) {
            log.error("Failed to access URL: {}", url, e);
            throw new ParsingException(
                    ParsingErrorType.CONNECTION_ERROR,
                    String.format("Failed to access page %s: %s", url, e.getMessage()),
                    e
            );
        }
    }

    private List<ParsedTestQuestionDto> parseTestPage(Document doc) {
        try {
            Elements questionElements = doc.select(".question-container");
            if (questionElements.isEmpty()) {
                log.warn("No questions found in document");
                throw new ParsingException(
                        ParsingErrorType.INVALID_PAGE_STRUCTURE,
                        "No questions found on the page"
                );
            }

            List<ParsedTestQuestionDto> questions = new ArrayList<>();
            for (Element questionElement : questionElements) {
                try {
                    questions.add(parseQuestion(questionElement));
                } catch (Exception e) {
                    log.error("Failed to parse question element", e);
                    throw new ParsingException(
                            ParsingErrorType.INVALID_CONTENT,
                            String.format("Failed to parse question: %s", e.getMessage()),
                            e
                    );
                }
            }
            log.debug("Successfully parsed {} questions", questions.size());
            return questions;

        } catch (ParsingException e) {
            throw e; // rethrow parsing exceptions
        } catch (Exception e) {
            log.error("Unexpected error during parsing", e);
            throw new ParsingException(
                    ParsingErrorType.UNEXPECTED_ERROR,
                    "Unexpected error during page parsing: " + e.getMessage(),
                    e
            );
        }
    }

    private ParsedTestQuestionDto parseQuestion(Element questionElement) {
        String questionText = extractQuestionText(questionElement);
        List<String> answers = extractAnswers(questionElement);
        String correctAnswer = extractCorrectAnswer(questionElement);

        return ParsedTestQuestionDto.builder()
                .questionText(questionText)
                .answers(answers)
                .correctAnswer(correctAnswer)
                .build();
    }

    private String extractQuestionText(Element questionElement) {
        Element questionTextElement = questionElement.selectFirst(TestParserConstants.QUESTION_SELECTOR);
        if (questionTextElement == null) {
            throw new ParsingException(
                    ParsingErrorType.MISSING_ELEMENT,
                    "Question text element not found"
            );
        }
        return questionTextElement.text().trim();
    }

    private List<String> extractAnswers(Element questionElement) {
        Elements answerElements = questionElement.select(TestParserConstants.ANSWERS_CONTAINER_SELECTOR);
        if (answerElements.isEmpty()) {
            throw new ParsingException(
                    ParsingErrorType.MISSING_ELEMENT,
                    "No answers found for question"
            );
        }

        return answerElements.stream()
                .map(this::extractSingleAnswer)
                .collect(Collectors.toList());
    }

    private String extractSingleAnswer(Element answerElement) {
        Element textElement = answerElement.selectFirst(TestParserConstants.ANSWER_TEXT_SELECTOR);
        if (textElement == null) {
            throw new ParsingException(
                    ParsingErrorType.MISSING_ELEMENT,
                    "Answer text element not found"
            );
        }
        return textElement.text().trim();
    }

    private String extractCorrectAnswer(Element questionElement) {
        Element correctAnswerElement = questionElement.selectFirst(TestParserConstants.CORRECT_ANSWER_SELECTOR);
        if (correctAnswerElement == null) {
            throw new ParsingException(
                    ParsingErrorType.MISSING_ELEMENT,
                    "Correct answer element not found"
            );
        }
        return correctAnswerElement.text().trim();
    }
}