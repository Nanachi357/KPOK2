package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.config.DebugProperties;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.service.navigation.NavigationSession;
import com.myprojects.kpok2.service.navigation.NavigationSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Component for parsing test pages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestParsingRunner {

    private final NavigationSessionFactory navigationSessionFactory;
    private final TestPageNavigator pageNavigator;
    private final TestQuestionParser questionParser;
    private final TestQuestionService testQuestionService;
    private final DebugProperties debugProperties;
    
    /**
     * Process test URL using existing navigation session
     */
    public boolean processTestUrl(String url, NavigationSession session) {
        log.info("Starting processing of URL: {}", url);
        
        try {
            WebDriver driver = session.getWebDriver();
            
            // Navigate to the test page
            boolean success = pageNavigator.navigateToTestUrl(driver, url);
            if (!success) {
                log.error("Failed to navigate to URL: {}", url);
                return false;
            }
            
            // Parse the test page
            List<ParsedTestQuestionDto> questions = questionParser.parsePage(driver, url);
            if (questions.isEmpty()) {
                log.error("Failed to parse questions at URL: {}", url);
                return false;
            }
            
            // Save parsed questions
            testQuestionService.saveUniqueQuestions(questions);
            
            // Save debug files if enabled
            if (debugProperties.isSaveFiles()) {
                pageNavigator.savePageSource(driver, url);
                pageNavigator.saveScreenshot(driver, url);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing URL {}: {}", url, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process test URL by creating new navigation session
     * @deprecated Use processTestUrl(String, NavigationSession) instead
     */
    @Deprecated
    public boolean processTestUrl(String url) {
        log.warn("Using deprecated method that creates new session. Use processTestUrl(String, NavigationSession) instead");
        return false;
    }
    
    /**
     * Parse the current page
     * @return List of parsed questions from the current page
     */
    private List<ParsedTestQuestionDto> parseCurrentPage() {
        log.info("Parsing the current page");
        
        // Parse current page
        Document doc = pageNavigator.getCurrentPageDocument();
        
        // Parse questions from the page
        List<ParsedTestQuestionDto> pageQuestions = questionParser.parseQuestions(doc);
        
        log.info("Found {} questions on the current page", pageQuestions.size());
        
        // Take screenshot of the page
        pageNavigator.takeScreenshot();
        
        return pageQuestions;
    }
} 