package com.myprojects.kpok2.service.navigation;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;

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
            }
        }
    }
    
    /**
     * Check if this session has an active test attempt
     */
    public boolean hasActiveAttempt() {
        return attemptId != null && !attemptId.isEmpty();
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