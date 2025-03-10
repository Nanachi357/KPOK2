package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Component for testing navigation to test pages in Docker environment
 * This will run when the application starts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerNavigationTester implements CommandLineRunner {

    private final WebDriver webDriver;
    private final AuthenticationService authenticationService;
    private final TestQuestionService testQuestionService;
    
    // CSS selectors for question elements
    private static final String QUESTION_SELECTOR = "div[id^=question-]";
    private static final String QUESTION_TEXT_SELECTOR = "div.qtext";
    private static final String ANSWER_OPTION_SELECTOR = "div.r0, div.r1";
    private static final String ANSWER_TEXT_SELECTOR = "div.flex-fill.ml-1";
    private static final String CORRECT_ANSWER_SELECTOR = "div.rightanswer";
    private static final String NEXT_PAGE_SELECTOR = "a.mod_quiz-next-nav";
    
    // Flag to control HTML file saving
    private static final boolean SAVE_HTML_FILES = false;
    
    // List of test URLs to process
    private static final List<String> TEST_URLS = Arrays.asList(
        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=1399436&cmid=109",
        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=1396254&cmid=109",
        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=1399441&cmid=109",
        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=1399446&cmid=109"
    );
    
    @Value("${testcenter.test-url:https://test.testcentr.org.ua/mod/quiz/review.php?attempt=123456}")
    private String testUrl;

    @Override
    public void run(String... args) {
        // List of URLs to process
        List<String> urlsToProcess = new ArrayList<>(TEST_URLS);
        
        // If command line argument is provided, use it instead of the predefined list
        if (args.length > 0 && args[0].startsWith("http")) {
            log.info("Command line URL provided. Using it instead of predefined list.");
            urlsToProcess.clear();
            urlsToProcess.add(args[0]);
        }
        
        log.info("Will process {} URLs", urlsToProcess.size());
        
        try {
            // Process each URL
            for (String url : urlsToProcess) {
                log.info("Processing URL: {}", url);
                boolean success = processTestUrl(url);
                log.info("Processing completed with result: {}", success ? "SUCCESS" : "FAILURE");
                
                // Add a small delay between processing URLs
                if (urlsToProcess.size() > 1) {
                    Thread.sleep(2000);
                }
            }
            
            // Keep the browser open for a while to inspect the result
            log.info("Keeping browser open for 10 seconds for inspection...");
            Thread.sleep(10000);
        } catch (Exception e) {
            log.error("Error during navigation test", e);
        } finally {
            // Don't close the WebDriver here if it's managed by Spring
            //  will close it when the application shuts down
            
            // Exit with appropriate status code
            System.exit(0);
        }
    }
    
    /**
     * Process a test URL by navigating to it and parsing all pages
     * @param testUrl URL of the test page to process
     * @return true if processing was successful, false otherwise
     */
    public boolean processTestUrl(String testUrl) {
        try {
            log.info("Starting processing of URL: {}", testUrl);
            
            // Step 1: Login using the existing AuthenticationService (if not already logged in)
            if (!isLoggedIn()) {
                log.info("Not logged in. Authenticating using AuthenticationService...");
                boolean loginSuccess = authenticationService.login();
                
                if (!loginSuccess) {
                    log.error("Authentication failed. Cannot proceed with navigation test.");
                    return false;
                }
                
                log.info("Authentication successful.");
            } else {
                log.info("Already logged in. Proceeding with navigation.");
            }
            
            // Step 2: Navigate to the target test URL
            log.info("Navigating to test URL: {}", testUrl);
            webDriver.get(testUrl);
            
            // Step 3: Wait for the page to load and verify access
            log.info("Waiting for page content to load...");
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div#page-content")));
            
            log.info("Navigation successful!");
            log.info("Current URL: {}", webDriver.getCurrentUrl());
            log.info("Page title: {}", webDriver.getTitle());
            
            // Step 4: Parse all pages of the test
            List<ParsedTestQuestionDto> allQuestions = parseAllPages();
            
            // Step 5: Save questions to database
            if (!allQuestions.isEmpty()) {
                log.info("Saving {} questions to database...", allQuestions.size());
                testQuestionService.saveUniqueQuestions(allQuestions, testUrl);
            } else {
                log.warn("No questions found to save.");
            }
            
            return true;
        } catch (Exception e) {
            log.error("Processing failed! Error: {}", e.getMessage());
            log.error("Current URL at failure: {}", webDriver.getCurrentUrl());
            log.error("Stack trace:", e);
            return false;
        }
    }
    
    /**
     * Parse all pages of the test
     * @return List of all parsed questions from all pages
     */
    private List<ParsedTestQuestionDto> parseAllPages() {
        List<ParsedTestQuestionDto> allQuestions = new ArrayList<>();
        int pageNumber = 1;
        boolean hasMorePages = true;
        
        while (hasMorePages) {
            log.info("Parsing page {} of the test", pageNumber);
            
            // Parse current page
            String pageSource = webDriver.getPageSource();
            
            // Save the HTML to a file for debugging if needed
            if (SAVE_HTML_FILES) {
                saveHtmlToFile(pageSource, "page_" + pageNumber);
            }
            
            // Parse questions from the page
            Document doc = Jsoup.parse(pageSource);
            List<ParsedTestQuestionDto> pageQuestions = parseQuestions(doc);
            
            log.info("Found {} questions on page {}", pageQuestions.size(), pageNumber);
            
            // Add questions from this page to the overall list
            allQuestions.addAll(pageQuestions);
            
            // Check if there's a next page
            hasMorePages = navigateToNextPage();
            pageNumber++;
        }
        
        log.info("Finished parsing all pages. Total questions found: {}", allQuestions.size());
        
        // Take screenshot of the last page
        takeScreenshot();
        
        return allQuestions;
    }
    
    /**
     * Navigate to the next page if available
     * @return true if navigation to next page was successful, false if there's no next page
     */
    private boolean navigateToNextPage() {
        try {
            WebElement nextPageLink = webDriver.findElement(By.cssSelector(NEXT_PAGE_SELECTOR));
            if (nextPageLink != null && nextPageLink.isDisplayed() && nextPageLink.isEnabled()) {
                log.info("Found next page link. Navigating to next page...");
                nextPageLink.click();
                
                // Wait for the next page to load
                WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div#page-content")));
                
                log.info("Successfully navigated to next page");
                log.info("Current URL: {}", webDriver.getCurrentUrl());
                return true;
            } else {
                log.info("No next page link found or it's not clickable");
                return false;
            }
        } catch (NoSuchElementException e) {
            log.info("No next page link found. This is the last page.");
            return false;
        } catch (Exception e) {
            log.error("Error navigating to next page: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if the user is already logged in
     * @return true if logged in, false otherwise
     */
    private boolean isLoggedIn() {
        try {
            // Try to find an element that's only visible when logged in
            return webDriver.findElements(By.cssSelector("div.usermenu")).size() > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Take a screenshot of the current page
     */
    private void takeScreenshot() {
        try {
            if (webDriver instanceof org.openqa.selenium.TakesScreenshot) {
                log.info("Taking screenshot of the page...");
                org.openqa.selenium.OutputType<byte[]> outputType = org.openqa.selenium.OutputType.BYTES;
                byte[] screenshot = ((org.openqa.selenium.TakesScreenshot) webDriver).getScreenshotAs(outputType);
                log.info("Screenshot taken, size: {} bytes", screenshot.length);
            }
        } catch (Exception e) {
            log.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }
    
    /**
     * Parses all questions from the document
     */
    private List<ParsedTestQuestionDto> parseQuestions(Document doc) {
        List<ParsedTestQuestionDto> questions = new ArrayList<>();
        Elements questionElements = doc.select(QUESTION_SELECTOR);
        
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
     * Prints a summary of the parsed questions
     */
    private void printQuestionsSummary(List<ParsedTestQuestionDto> questions) {
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
    
    /**
     * Saves the HTML content to a file for debugging
     */
    private void saveHtmlToFile(String html, String prefix) {
        if (!SAVE_HTML_FILES) {
            return; // Skip saving if disabled
        }
        
        File outputFile = null;
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = prefix + "_" + timestamp + ".html";
            
            outputFile = new File(fileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(html.getBytes());
            }
            
            log.info("HTML content saved to file: {}", outputFile.getAbsolutePath());
            
            // Optionally delete the file immediately after saving (for testing purposes)
            // outputFile.deleteOnExit(); // Uncomment to delete on JVM exit
        } catch (Exception e) {
            log.warn("Failed to save HTML content to file: {}", e.getMessage());
        }
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    private void saveHtmlToFile(String html) {
        saveHtmlToFile(html, "test_page");
    }
} 