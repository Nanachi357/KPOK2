package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.AuthenticationService;
import com.myprojects.kpok2.service.TestQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Component for running test parsing at application startup
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestParsingRunner implements CommandLineRunner {

    private final AuthenticationService authenticationService;
    private final TestPageNavigator pageNavigator;
    private final TestQuestionParser questionParser;
    private final TestQuestionService testQuestionService;
    
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
        // Temporarily disable TestParsingRunner during multi-threaded navigation testing
        // TODO: Set to true when parser integration is needed
        boolean parserEnabled = false;
        
        if (!parserEnabled) {
            log.info("TestParsingRunner is disabled. Skipping execution.");
            return;
        }
        
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
            if (!authenticationService.isLoggedIn()) {
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
            if (!pageNavigator.navigateToTestUrl(testUrl)) {
                log.error("Navigation failed. Cannot proceed with parsing.");
                return false;
            }
            
            // Step 3: Parse all pages of the test
            List<ParsedTestQuestionDto> allQuestions = parseAllPages();
            
            // Step 4: Save questions to database
            if (!allQuestions.isEmpty()) {
                log.info("Saving {} questions to database...", allQuestions.size());
                testQuestionService.saveUniqueQuestions(allQuestions);
            } else {
                log.warn("No questions found to save.");
            }
            
            return true;
        } catch (Exception e) {
            log.error("Processing failed! Error: {}", e.getMessage());
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
            Document doc = pageNavigator.getCurrentPageDocument();
            
            // Parse questions from the page
            List<ParsedTestQuestionDto> pageQuestions = questionParser.parseQuestions(doc);
            
            log.info("Found {} questions on page {}", pageQuestions.size(), pageNumber);
            
            // Add questions from this page to the overall list
            allQuestions.addAll(pageQuestions);
            
            // Check if there's a next page
            hasMorePages = pageNavigator.navigateToNextPage();
            pageNumber++;
        }
        
        log.info("Finished parsing all pages. Total questions found: {}", allQuestions.size());
        
        // Take screenshot of the last page
        pageNavigator.takeScreenshot();
        
        return allQuestions;
    }
} 