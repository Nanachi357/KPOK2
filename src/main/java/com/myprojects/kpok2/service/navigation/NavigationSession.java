package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.model.QuizTarget;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import java.util.List;

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
    private final QuizTarget quizTarget;
    
    @Getter
    @Setter
    private String currentUrl;
    
    @Getter
    @Setter
    private String attemptId;
    
    @Getter
    private List<String> resultPageUrls;
    
    private final WebDriverFactory webDriverFactory;
    private final AccountManager accountManager;
    
    /**
     * Create a new navigation session with the specified resources
     */
    public NavigationSession(WebDriverFactory webDriverFactory, AccountManager accountManager, 
                             WebDriver webDriver, AccountCredentials account, QuizTarget quizTarget) {
        this.webDriverFactory = webDriverFactory;
        this.accountManager = accountManager;
        this.webDriver = webDriver;
        this.account = account;
        this.quizTarget = quizTarget;
        
        log.info("Created new navigation session for account: {}", account.getUsername());
    }
    
    /**
     * Update the current URL and extract attemptId if present.
     * 
     * @param url The new URL
     */
    public void updateUrl(String url) {
        this.currentUrl = url;
        
        QuizTarget.extractAttemptId(url).ifPresent(id -> {
            attemptId = id;
            resultPageUrls = List.of(quizTarget.reviewUrl(id));
            log.debug("Extracted attemptId: {}", attemptId);
        });
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
        this.resultPageUrls = resultPageUrls == null ? List.of() : List.copyOf(resultPageUrls);
        log.debug("Stored {} result page URLs", this.resultPageUrls.size());
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
