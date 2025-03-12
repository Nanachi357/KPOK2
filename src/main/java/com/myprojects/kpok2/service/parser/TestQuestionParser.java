package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
                questions.add(question);
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
        // Get question text
        String questionText = questionElement.select(TestParserConstants.QUESTION_TEXT_SELECTOR).text();
        
        // Get answer options
        Elements answerElements = questionElement.select(TestParserConstants.ANSWER_OPTION_SELECTOR);
        List<String> answers = new ArrayList<>();
        
        for (Element answerElement : answerElements) {
            String answerText = answerElement.select(TestParserConstants.ANSWER_TEXT_SELECTOR).text();
            answers.add(answerText);
        }
        
        // Get correct answer
        String correctAnswerText = questionElement.select(TestParserConstants.CORRECT_ANSWER_SELECTOR).text();
        // Remove the prefix "Correct answer: "
        String correctAnswer = correctAnswerText.replace(TestParserConstants.CORRECT_ANSWER_PREFIX, "");
        
        return ParsedTestQuestionDto.builder()
                .questionText(questionText)
                .normalizedText(normalizer.normalizeText(questionText))
                .answers(answers)
                .correctAnswer(correctAnswer)
                .normalizedCorrectAnswer(normalizer.normalizeText(correctAnswer))
                .build();
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