package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.stream.Collectors;

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
    private final AccountConfigurationService accountService;

    @Autowired
    public NavigationSessionFactory(
            AccountManager accountManager,
            WebDriverFactory webDriverFactory,
            TestCenterProperties testCenterProperties,
            AccountConfigurationService accountService) {
        this.accountManager = accountManager;
        this.webDriverFactory = webDriverFactory;
        this.testCenterProperties = testCenterProperties;
        this.accountService = accountService;
    }

    /**
     * Initializes the account pool from configuration.
     */
    @PostConstruct
    public void initializeAccountPool() {
        log.info("Initializing account pool for navigation...");
        
        // First try to get accounts from the account service
        List<AccountDTO> enabledAccounts = accountService.getAccounts().stream()
                .filter(AccountDTO::isEnabled)
                .collect(Collectors.toList());
        
        if (!enabledAccounts.isEmpty()) {
            log.info("Using accounts from account manager");
            
            // Add all active accounts to the pool
            for (AccountDTO account : enabledAccounts) {
                accountManager.addAccount(account.getUsername(), account.getPassword());
                log.info("Added account to pool: {}", account.getUsername());
            }
        } else {
            // Fall back to TestCenterProperties accounts
            var configAccounts = testCenterProperties.getEnabledAccounts();
            
            if (configAccounts.isEmpty()) {
                log.warn("No TestCenter accounts found in configuration! Navigation will not work properly.");
                return;
            }
            
            log.info("Using accounts from configuration properties");
            
            // Add all active accounts from config to the pool
            for (var account : configAccounts) {
                accountManager.addAccount(account.getUsername(), account.getPassword());
                log.info("Added account to pool: {}", account.getUsername());
            }
        }
        
        // Set navigation settings from account service if available
        int maxThreads = accountService.getMaxThreads();
        int threadTimeout = accountService.getThreadTimeout();
        
        // Update TestCenterProperties with these values
        testCenterProperties.getNavigation().setMaxThreads(maxThreads);
        testCenterProperties.getNavigation().setThreadTimeoutSeconds(threadTimeout);
        
        log.info("Account pool initialized with {} accounts", accountManager.getAccountCount());
        log.info("Navigation settings: maxThreads={}, threadTimeout={}s", maxThreads, threadTimeout);
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
        
        // Initialize the session with reusable properties
        NavigationSession session = new NavigationSession(webDriverFactory, accountManager, webDriver, account);
        log.debug("Created new session with account: {} and driver: {}", account.getUsername(), webDriver.hashCode());
        return session;
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