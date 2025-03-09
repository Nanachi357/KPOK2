package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.TestParsingResultDto;

import java.util.List;

/**
 * Class for testing and comparing different parsers
 */
public class ParserTester {

    public static void main(String[] args) {
        String filePath = "data.html"; // Path to HTML file
        
        // Test first parser
        System.out.println("\n========== TESTING LocalHtmlQuestionParser ==========");
        LocalHtmlQuestionParser parser1 = new LocalHtmlQuestionParser();
        TestParsingResultDto result1 = parser1.parseLocalHtmlFile(filePath);
        
        // Print questions from first parser
        printQuestions(result1.getQuestions(), "LocalHtmlQuestionParser");
        
        // Test second parser
        System.out.println("\n========== TESTING PageContentQuestionParser ==========");
        PageContentQuestionParser parser2 = new PageContentQuestionParser();
        TestParsingResultDto result2 = parser2.parseLocalHtmlFile(filePath);
        
        // Print questions from second parser
        printQuestions(result2.getQuestions(), "PageContentQuestionParser");
        
        // Compare results
        compareResults(result1, result2);
    }
    
    /**
     * Prints detailed information about questions
     */
    private static void printQuestions(List<ParsedTestQuestionDto> questions, String parserName) {
        System.out.println("\n=== Question Content (" + parserName + ") ===");
        
        if (questions == null || questions.isEmpty()) {
            System.out.println("No questions found or empty list");
            return;
        }
        
        System.out.println("Total number of questions: " + questions.size());
        
        // Limit the number of questions to display to avoid console overload
        int maxQuestionsToShow = Math.min(questions.size(), 10);
        
        for (int i = 0; i < maxQuestionsToShow; i++) {
            ParsedTestQuestionDto question = questions.get(i);
            System.out.println("\n--- Question " + (i + 1) + " ---");
            System.out.println("Text: " + question.getQuestionText());
            
            System.out.println("Answer options:");
            List<String> answers = question.getAnswers();
            for (int j = 0; j < answers.size(); j++) {
                System.out.println("  " + (char)('A' + j) + ". " + answers.get(j));
            }
            
            System.out.println("Correct answer: " + question.getCorrectAnswer());
        }
        
        if (questions.size() > maxQuestionsToShow) {
            System.out.println("\n... and " + (questions.size() - maxQuestionsToShow) + " more questions (not shown)");
        }
    }
    
    /**
     * Compares results from two parsers
     */
    private static void compareResults(TestParsingResultDto result1, TestParsingResultDto result2) {
        System.out.println("\n========== RESULTS COMPARISON ==========");
        
        if (result1.getStatus() != result2.getStatus()) {
            System.out.println("Different parsing statuses:");
            System.out.println("LocalHtmlQuestionParser: " + result1.getStatus());
            System.out.println("PageContentQuestionParser: " + result2.getStatus());
        } else {
            System.out.println("Parsing statuses are the same: " + result1.getStatus());
        }
        
        if (result1.getErrorMessage() != null || result2.getErrorMessage() != null) {
            System.out.println("Error messages:");
            System.out.println("LocalHtmlQuestionParser: " + result1.getErrorMessage());
            System.out.println("PageContentQuestionParser: " + result2.getErrorMessage());
            return;
        }
        
        List<ParsedTestQuestionDto> questions1 = result1.getQuestions();
        List<ParsedTestQuestionDto> questions2 = result2.getQuestions();
        
        System.out.println("Number of questions found:");
        System.out.println("LocalHtmlQuestionParser: " + questions1.size());
        System.out.println("PageContentQuestionParser: " + questions2.size());
        
        // Compare each question
        int matchCount = 0;
        int mismatchCount = 0;
        
        int minSize = Math.min(questions1.size(), questions2.size());
        for (int i = 0; i < minSize; i++) {
            ParsedTestQuestionDto q1 = questions1.get(i);
            ParsedTestQuestionDto q2 = questions2.get(i);
            
            boolean textMatch = q1.getNormalizedText().equals(q2.getNormalizedText());
            boolean answerMatch = q1.getNormalizedCorrectAnswer().equals(q2.getNormalizedCorrectAnswer());
            boolean optionsMatch = q1.getAnswers().size() == q2.getAnswers().size();
            
            if (textMatch && answerMatch && optionsMatch) {
                matchCount++;
            } else {
                mismatchCount++;
                System.out.println("\nMismatch in question #" + (i + 1) + ":");
                if (!textMatch) {
                    System.out.println("Different question text:");
                    System.out.println("Parser1: " + q1.getQuestionText());
                    System.out.println("Parser2: " + q2.getQuestionText());
                }
                if (!answerMatch) {
                    System.out.println("Different correct answers:");
                    System.out.println("Parser1: " + q1.getCorrectAnswer());
                    System.out.println("Parser2: " + q2.getCorrectAnswer());
                }
                if (!optionsMatch) {
                    System.out.println("Different number of answer options:");
                    System.out.println("Parser1: " + q1.getAnswers().size());
                    System.out.println("Parser2: " + q2.getAnswers().size());
                }
            }
        }
        
        System.out.println("\nComparison results:");
        System.out.println("Matching questions: " + matchCount);
        System.out.println("Questions with differences: " + mismatchCount);
        
        if (questions1.size() != questions2.size()) {
            System.out.println("Different number of questions in parser results!");
        }
    }
} 