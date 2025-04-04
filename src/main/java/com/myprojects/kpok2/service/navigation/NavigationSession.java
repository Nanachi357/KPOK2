package com.myprojects.kpok2.service.navigation;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a navigation session with its associated resources.
 * Each thread will have its own NavigationSession.
 */
@Slf4j
public class NavigationSession implements AutoCloseable {
    
    @Getter
    private final WebDriver webDriver;
    
    @Getter
    private final AccountCredentials account;
    
    @Getter
    @Setter
    private String currentUrl;
    
    @Getter
    @Setter
    private String attemptId;
    
    @Getter
    @Setter
    private List<String> resultPageUrls;
    
    private final WebDriverFactory webDriverFactory;
    private final AccountManager accountManager;
    
    /**
     * Create a new navigation session with the specified resources
     */
    public NavigationSession(WebDriverFactory webDriverFactory, AccountManager accountManager, 
                             WebDriver webDriver, AccountCredentials account) {
        this.webDriverFactory = webDriverFactory;
        this.accountManager = accountManager;
        this.webDriver = webDriver;
        this.account = account;
        
        log.info("Created new navigation session for account: {}", account.getUsername());
    }
    
    /**
     * Update the current URL and extract attemptId if present.
     * 
     * @param url The new URL
     */
    public void updateUrl(String url) {
        this.currentUrl = url;
        
        // Extract attemptId from URL if present
        if (url != null && url.contains("attempt=")) {
            String[] parts = url.split("attempt=");
            if (parts.length > 1) {
                String idPart = parts[1];
                attemptId = idPart.split("&")[0];
                log.debug("Extracted attemptId: {}", attemptId);
                
                // Generate result page URLs since we have a valid attemptId
                if (attemptId != null && !attemptId.isEmpty()) {
                    generateResultPageUrls();
                }
            }
        }
    }
    
    /**
     * Generate URLs for result pages based on the current attemptId
     */
    private void generateResultPageUrls() {
        if (attemptId == null || attemptId.isEmpty()) {
            log.warn("Cannot generate result page URLs: No attempt ID available");
            return;
        }
        
        String baseResultUrl = "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=" + 
                              attemptId + "&cmid=109";
        String page1Url = baseResultUrl + "&page=1";
        String page2Url = baseResultUrl + "&page=2";
        
        // Create a list of result page URLs
        List<String> urls = new ArrayList<>();
        urls.add(baseResultUrl);
        urls.add(page1Url);
        urls.add(page2Url);
        
        // Store URLs in session
        this.resultPageUrls = urls;
        
        log.debug("Generated result page URLs: {}", urls);
    }
    
    /**
     * Check if this session has an active test attempt
     */
    public boolean hasActiveAttempt() {
        return attemptId != null && !attemptId.isEmpty();
    }
    
    /**
     * Set the URLs for all result pages
     * 
     * @param resultPageUrls List of URLs for result pages
     */
    public void setResultPageUrls(List<String> resultPageUrls) {
        this.resultPageUrls = resultPageUrls;
        log.debug("Stored {} result page URLs", resultPageUrls.size());
    }
    
    /**
     * Close the session and release all resources.
     * This method is called automatically when using try-with-resources.
     */
    @Override
    public void close() {
        log.info("Closing navigation session for account: {}", account.getUsername());
        accountManager.releaseAccount(account);
        webDriverFactory.closeDriver();
    }
} 