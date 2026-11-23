package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;

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
        refreshAccountPool();
    }

    /**
     * Rebuilds the navigation pool from the latest saved account settings.
     * A configured-but-disabled local account list intentionally produces an
     * empty pool instead of silently falling back to legacy credentials.
     */
    public synchronized boolean refreshAccountPool() {
        log.info("Initializing account pool for navigation...");

        List<AccountDTO> configuredAccounts = List.copyOf(accountService.getAccounts());
        List<AccountCredentials> poolAccounts;
        if (!configuredAccounts.isEmpty()) {
            poolAccounts = configuredAccounts.stream()
                    .filter(AccountDTO::isEnabled)
                    .map(account -> new AccountCredentials(
                            account.getUsername(), account.getPassword()))
                    .toList();
            log.info("Using accounts from account manager");
        } else {
            poolAccounts = testCenterProperties.getEnabledAccounts().stream()
                    .map(account -> new AccountCredentials(
                            account.getUsername(), account.getPassword()))
                    .toList();
            log.info("Using accounts from configuration properties");
        }

        boolean applied = accountManager.replaceAccounts(poolAccounts);

        // Set navigation settings from account service if available
        int maxThreads = accountService.getMaxThreads();
        int threadTimeout = accountService.getThreadTimeout();
        
        // Update TestCenterProperties with these values
        testCenterProperties.getNavigation().setMaxThreads(maxThreads);
        testCenterProperties.getNavigation().setThreadTimeoutSeconds(threadTimeout);
        
        if (accountManager.getAccountCount() == 0) {
            log.warn("No enabled TestCenter accounts are configured");
        }
        log.info("Account pool initialized with {} accounts", accountManager.getAccountCount());
        log.info("Navigation settings: maxThreads={}, threadTimeout={}s", maxThreads, threadTimeout);
        return applied;
    }

    public int getAccountCount() {
        return accountManager.getAccountCount();
    }
    
    /**
     * Create a new navigation session for the current thread.
     * This method automatically allocates an account and WebDriver for use.
     *
     * @return NavigationSession ready to use.
     * @throws InterruptedException if the thread was interrupted while waiting for an available account.
     */
    public NavigationSession createSession() throws InterruptedException {
        // Resolve the selected quiz before reserving an account. A missing or
        // malformed URL therefore fails fast without leaking pooled resources.
        QuizTarget quizTarget = accountService.getQuizTarget();
        int timeoutSeconds = Math.min(
                300,
                Math.max(
                        1,
                        testCenterProperties.getNavigation()
                                .getAccountAcquireTimeoutSeconds()));
        AccountCredentials account = accountManager.acquireAccount(
                Duration.ofSeconds(timeoutSeconds));
        WebDriver webDriver;
        try {
            webDriver = webDriverFactory.getDriver();
        } catch (RuntimeException | Error e) {
            accountManager.releaseAccount(account);
            throw e;
        }
        
        // Initialize the session with reusable properties
        NavigationSession session = new NavigationSession(
                webDriverFactory, accountManager, webDriver, account, quizTarget);
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
