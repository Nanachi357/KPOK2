package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.TestParsingResultDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for extracting test questions from local HTML files
 */
@Service
public class LocalHtmlQuestionParser {
    private static final Logger logger = LoggerFactory.getLogger(LocalHtmlQuestionParser.class);
    
    // CSS selectors for question elements
    private static final String QUESTION_SELECTOR = "div[id^=question-]";
    private static final String QUESTION_TEXT_SELECTOR = "div.qtext";
    private static final String ANSWER_OPTION_SELECTOR = "div.r0, div.r1";
    private static final String ANSWER_TEXT_SELECTOR = "div.flex-fill.ml-1";
    private static final String CORRECT_ANSWER_SELECTOR = "div.rightanswer";
    
    /**
     * Main method to run the parser
     */
    public static void main(String[] args) {
        LocalHtmlQuestionParser parser = new LocalHtmlQuestionParser();
        String filePath = "data.html"; // Path to your HTML file
        
        TestParsingResultDto result = parser.parseLocalHtmlFile(filePath);
        parser.printResults(result);
    }
    
    /**
     * Parses an HTML file containing test questions
     * @param filePath path to the HTML file
     * @return parsing result with questions
     */
    public TestParsingResultDto parseLocalHtmlFile(String filePath) {
        logger.info("Starting to parse local HTML file: {}", filePath);
        System.out.println("Starting to parse local HTML file: " + filePath);
        
        try {
            File input = new File(filePath);
            Document doc = Jsoup.parse(input, "UTF-8");
            
            List<ParsedTestQuestionDto> questions = parseQuestions(doc);
            
            logger.info("Parsing completed successfully. Found {} questions", questions.size());
            System.out.println("Parsing completed successfully. Found " + questions.size() + " questions");
            
            return TestParsingResultDto.builder()
                    .testUrl(filePath)
                    .status(ParsingStatus.SUCCESS)
                    .questions(questions)
                    .build();
            
        } catch (IOException e) {
            logger.error("Error reading file: {}", e.getMessage());
            System.err.println("Error reading file: " + e.getMessage());
            return TestParsingResultDto.builder()
                    .testUrl(filePath)
                    .status(ParsingStatus.FAILED)
                    .errorMessage("Error reading file: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            logger.error("Error during parsing: {}", e.getMessage());
            System.err.println("Error during parsing: " + e.getMessage());
            return TestParsingResultDto.builder()
                    .testUrl(filePath)
                    .status(ParsingStatus.FAILED)
                    .errorMessage("Error during parsing: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Parses all questions from the document
     */
    private List<ParsedTestQuestionDto> parseQuestions(Document doc) {
        List<ParsedTestQuestionDto> questions = new ArrayList<>();
        Elements questionElements = doc.select(QUESTION_SELECTOR);
        
        logger.info("Found {} question elements", questionElements.size());
        System.out.println("Found " + questionElements.size() + " question elements");
        
        for (Element questionElement : questionElements) {
            try {
                ParsedTestQuestionDto question = parseQuestion(questionElement);
                questions.add(question);
            } catch (Exception e) {
                logger.warn("Error parsing question: {}", e.getMessage());
                System.err.println("Error parsing question: " + e.getMessage());
            }
        }
        
        return questions;
    }
    
    /**
     * Parses an individual question
     */
    private ParsedTestQuestionDto parseQuestion(Element questionElement) {
        // Get question text
        String questionText = questionElement.select(QUESTION_TEXT_SELECTOR).text();
        
        // Get answer options
        Elements answerElements = questionElement.select(ANSWER_OPTION_SELECTOR);
        List<String> answers = new ArrayList<>();
        
        for (Element answerElement : answerElements) {
            String answerText = answerElement.select(ANSWER_TEXT_SELECTOR).text();
            answers.add(answerText);
        }
        
        // Get correct answer
        String correctAnswerText = questionElement.select(CORRECT_ANSWER_SELECTOR).text();
        // Remove the prefix "Correct answer: "
        String correctAnswer = correctAnswerText.replace("Правильна відповідь: ", "");
        
        return ParsedTestQuestionDto.builder()
                .questionText(questionText)
                .normalizedText(normalizeText(questionText))
                .answers(answers)
                .correctAnswer(correctAnswer)
                .normalizedCorrectAnswer(normalizeText(correctAnswer))
                .build();
    }
    
    /**
     * Normalizes text for comparison
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[^а-яa-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    
    /**
     * Prints parsing results to the console
     */
    public void printResults(TestParsingResultDto result) {
        System.out.println("\n=== Parsing Results (LocalHtmlQuestionParser) ===");
        System.out.println("Status: " + result.getStatus());
        
        if (result.getErrorMessage() != null) {
            System.out.println("Error: " + result.getErrorMessage());
            return;
        }
        
        List<ParsedTestQuestionDto> questions = result.getQuestions();
        System.out.println("Number of questions: " + questions.size());
        
        for (int i = 0; i < questions.size(); i++) {
            ParsedTestQuestionDto q = questions.get(i);
            System.out.println("\nQuestion " + (i + 1) + ": " + q.getQuestionText());
            System.out.println("Answer options:");
            for (int j = 0; j < q.getAnswers().size(); j++) {
                System.out.println("  " + (char)('a' + j) + ". " + q.getAnswers().get(j));
            }
            System.out.println("Correct answer: " + q.getCorrectAnswer());
        }
    }
} 