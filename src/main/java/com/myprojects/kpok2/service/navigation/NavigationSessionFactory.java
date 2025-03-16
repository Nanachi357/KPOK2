package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Factory for creating navigation sessions.
 * Responsible for initializing the account pool and creating navigation sessions.
 */
@Slf4j
@Component
public class NavigationSessionFactory {

    private final AccountManager accountManager;
    private final WebDriverFactory webDriverFactory;
    private final TestCenterProperties testCenterProperties;

    @Autowired
    public NavigationSessionFactory(
            AccountManager accountManager,
            WebDriverFactory webDriverFactory,
            TestCenterProperties testCenterProperties) {
        this.accountManager = accountManager;
        this.webDriverFactory = webDriverFactory;
        this.testCenterProperties = testCenterProperties;
    }

    /**
     * Initializes the account pool from configuration.
     */
    @PostConstruct
    public void initializeAccountPool() {
        log.info("Initializing account pool for navigation...");
        
        var enabledAccounts = testCenterProperties.getEnabledAccounts();
        
        if (enabledAccounts.isEmpty()) {
            log.warn("No TestCenter accounts found in configuration! Navigation will not work properly.");
            return;
        }
        
        // Add all active accounts to the pool
        for (var account : enabledAccounts) {
            accountManager.addAccount(account.getUsername(), account.getPassword());
            log.info("Added account to pool: {}", account.getUsername());
        }
        
        log.info("Account pool initialized with {} accounts", accountManager.getAccountCount());
        log.info("Maximum parallel navigation threads: {}", testCenterProperties.getNavigation().getMaxThreads());
    }
    
    /**
     * Create a new navigation session for the current thread.
     * This method automatically allocates an account and WebDriver for use.
     *
     * @return NavigationSession ready to use.
     * @throws InterruptedException if the thread was interrupted while waiting for an available account.
     */
    public NavigationSession createSession() throws InterruptedException {
        AccountCredentials account = accountManager.acquireAccount();
        WebDriver webDriver = webDriverFactory.getDriver();
        
        return new NavigationSession(webDriverFactory, accountManager, webDriver, account);
    }
    
    /**
     * Close all resources when the application shuts down.
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up navigation resources...");
        webDriverFactory.closeAllDrivers();
    }
} 