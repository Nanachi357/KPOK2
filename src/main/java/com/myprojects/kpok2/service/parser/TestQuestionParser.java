package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Component responsible for parsing test questions from HTML
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestQuestionParser {

    private final TestNormalizer normalizer;

    /**
     * Parse test questions from the current page using WebDriver
     */
    public List<ParsedTestQuestionDto> parsePage(WebDriver driver, String url) {
        try {
            // Get page source and parse with Jsoup
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);
            return parseQuestions(doc);
        } catch (Exception e) {
            log.error("Error parsing page {}: {}", url, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses all questions from the document
     * @param doc JSoup Document containing the HTML of the test page
     * @return List of parsed test questions
     */
    public List<ParsedTestQuestionDto> parseQuestions(Document doc) {
        List<ParsedTestQuestionDto> questions = new ArrayList<>();
        Elements questionElements = doc.select(TestParserConstants.QUESTION_SELECTOR);
        
        log.info("Found {} question elements", questionElements.size());
        
        for (Element questionElement : questionElements) {
            try {
                ParsedTestQuestionDto question = parseQuestion(questionElement);
                if (question != null) {
                    questions.add(question);
                }
            } catch (Exception e) {
                log.warn("Error parsing question: {}", e.getMessage());
            }
        }
        
        return questions;
    }
    
    /**
     * Parses an individual question
     * @param questionElement JSoup Element containing the question HTML
     * @return Parsed test question
     */
    private ParsedTestQuestionDto parseQuestion(Element questionElement) {
        try {
            // Get question text
            Element questionTextElement = questionElement.selectFirst("div.qtext");
            if (questionTextElement == null) {
                log.warn("Question text not found in container");
                return null;
            }
            String questionText = questionTextElement.text().trim();
            
            // Get correct answer
            Element correctAnswerElement = questionElement.selectFirst("div.rightanswer");
            if (correctAnswerElement == null) {
                log.warn("Correct answer not found in container");
                return null;
            }
            String correctAnswer = correctAnswerElement.text().trim();
            // Remove the prefix "Correct answer: " if present
            correctAnswer = correctAnswer.replace(TestParserConstants.CORRECT_ANSWER_PREFIX, "").trim();
            
            // Get answer options
            Elements options = questionElement.select("div.answer div.r0, div.answer div.r1");
            List<String> answers = new ArrayList<>();
            for (Element option : options) {
                String optionText = option.text().trim();
                if (!optionText.isEmpty()) {
                    answers.add(optionText);
                }
            }
            
            // Create and return question DTO
            return ParsedTestQuestionDto.builder()
                    .questionText(questionText)
                    .normalizedText(normalizer.normalizeText(questionText))
                    .answers(answers)
                    .correctAnswer(correctAnswer)
                    .normalizedCorrectAnswer(normalizer.normalizeText(correctAnswer))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error parsing question container: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Prints a summary of the parsed questions
     * @param questions List of parsed questions
     */
    public void printQuestionsSummary(List<ParsedTestQuestionDto> questions) {
        if (questions.isEmpty()) {
            log.info("No questions found on the page");
            return;
        }
        
        log.info("=== Questions Summary ===");
        log.info("Total questions found: {}", questions.size());
        
        // Print details for the first few questions
        int questionsToShow = Math.min(5, questions.size());
        for (int i = 0; i < questionsToShow; i++) {
            ParsedTestQuestionDto question = questions.get(i);
            log.info("Question {}: {}", i + 1, question.getQuestionText());
            log.info("  Answer options: {}", question.getAnswers().size());
            for (int j = 0; j < question.getAnswers().size(); j++) {
                log.info("    {}. {}", (char)('A' + j), question.getAnswers().get(j));
            }
            log.info("  Correct answer: {}", question.getCorrectAnswer());
            log.info("---");
        }
        
        if (questions.size() > questionsToShow) {
            log.info("... and {} more questions", questions.size() - questionsToShow);
        }
    }
} 