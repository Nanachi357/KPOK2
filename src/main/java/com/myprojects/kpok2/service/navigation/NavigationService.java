package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.parser.TestParsingRunner;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.By;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.time.Duration;
import com.myprojects.kpok2.service.navigation.TestCenterNavigator;

/**
 * Service for managing navigation processes with multi-threading support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {
    private final TestCenterProperties properties;
    private final TestCenterNavigator navigator;
    private final NavigationSessionFactory sessionFactory;
    private final TestParsingRunner testParsingRunner;
    private final TestParsingStatistics parsingStatistics;
    private final AccountConfigurationService accountService;
    
    private ExecutorService executorService;
    private final List<Future<?>> runningTasks = new ArrayList<>();
    
    /**
     * Start navigation process with the configured number of threads.
     * @return true if navigation has started successfully
     */
    public boolean startNavigation() {
        int threadCount = properties.getNavigation().getMaxThreads();
        
        // Validate thread count
        if (threadCount <= 0) {
            log.error("Invalid thread count: {}", threadCount);
            return false;
        }
        
        // Check if navigation is already running
        if (executorService != null && !executorService.isShutdown()) {
            log.warn("Navigation is already running");
            return false;
        }
        
        log.info("Initializing navigation with {} threads", threadCount);
        
        // Set up iteration counting
        int iterationCount = accountService.getIterationCount();
        parsingStatistics.setTotalIterationsNeeded(iterationCount);
        
        // Log session reuse setting
        boolean reuseSession = accountService.isReuseSession();
        if (reuseSession) {
            log.info("Session reuse is ENABLED - browser sessions will be kept open between iterations");
        } else {
            log.info("Session reuse is DISABLED - new browser sessions will be created for each iteration");
        }
        
        if (iterationCount <= 0) {
            log.info("Running in unlimited iteration mode");
        } else {
            log.info("Target iteration count set to: {}", iterationCount);
        }
        
        // Create thread pool
        executorService = Executors.newFixedThreadPool(threadCount);
        
        // Start tasks for each thread
        for (int i = 0; i < threadCount; i++) {
            String threadName = "NavigationThread-" + i;
            Future<?> future = executorService.submit(new NavigationTask(threadName));
            runningTasks.add(future);
            
            log.info("Started navigation task: {}", threadName);
        }
        
        // Check if any tasks have started successfully
        return !runningTasks.isEmpty();
    }
    
    /**
     * Shutdown all navigation processes, ensuring resources are properly closed.
     */
    public void shutdown() {
        log.info("Shutting down navigation service");
        
        if (executorService != null) {
            try {
                // Try to shutdown gracefully first
                executorService.shutdown();
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't terminate in time
                    log.warn("Navigation tasks did not terminate in time, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for navigation tasks to complete", e);
                // Force shutdown
                executorService.shutdownNow();
                // Restore interrupted status
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("Navigation service shutdown complete");
    }
    
    /**
     * Task that handles the navigation process for one account.
     */
    private class NavigationTask implements Callable<Boolean> {
        private final String threadName;
        
        public NavigationTask(String threadName) {
            this.threadName = threadName;
        }
        
        @Override
        public Boolean call() {
            Thread.currentThread().setName(threadName);
            log.info("Starting navigation task: {}", threadName);
            
            NavigationSession session = null;
            boolean reuseSession = accountService.isReuseSession();
            
            try {
                // Main iteration loop
                while (!Thread.currentThread().isInterrupted() && parsingStatistics.isMoreIterationsNeeded()) {
                    // Before starting a new iteration, check again if we should continue
                    if (!parsingStatistics.isMoreIterationsNeeded()) {
                        log.info("{}: No more iterations needed, exiting loop", threadName);
                        break;
                    }
                    
                    // Create a new session only if we don't have one or reuse is disabled
                    if (session == null) {
                        try {
                            session = sessionFactory.createSession();
                            log.info("{}: Created new navigation session with account: {}",
                                    threadName, session.getAccount().getUsername());
                        } catch (Exception e) {
                            log.error("{}: Failed to create session: {}", threadName, e.getMessage());
                            // Wait a bit before retrying
                            Thread.sleep(5000);
                            continue;
                        }
                    }
                    
                    // Run one iteration of the parsing process
                    boolean iterationSuccess = runOneIteration(session);
                    
                    if (iterationSuccess) {
                        // Count this as a completed iteration
                        int completedCount = parsingStatistics.incrementCompletedIterations();
                        int totalNeeded = parsingStatistics.getTotalIterationsNeeded();
                        
                        if (totalNeeded > 0) {
                            log.info("{}: Completed iteration {}/{}", threadName, completedCount, totalNeeded);
                        } else {
                            log.info("{}: Completed iteration {} (unlimited mode)", threadName, completedCount);
                        }
                        
                        // Check if we've reached the total
                        if (!parsingStatistics.isMoreIterationsNeeded()) {
                            log.info("{}: Target iteration count reached, exiting loop", threadName);
                            break;
                        }
                        
                        // If we're not reusing sessions between iterations, close the current one
                        if (!reuseSession) {
                            if (session != null) {
                                try {
                                    session.close();
                                    log.info("{}: Closed navigation session after successful iteration (session reuse disabled)", 
                                            threadName);
                                    session = null;
                                } catch (Exception e) {
                                    log.warn("{}: Error closing session: {}", threadName, e.getMessage());
                                    session = null; // Still null it even if close failed
                                }
                            }
                        } else {
                            log.info("{}: Keeping navigation session open for next iteration (session reuse enabled)", 
                                     threadName);
                            
                            // Navigate back to test page to prepare for next iteration
                            try {
                                String testUrl = "https://test.testcentr.org.ua/mod/quiz/view.php?id=109"; // Direct usage of test page URL
                                String loginUrl = "https://test.testcentr.org.ua/login/index.php"; // Login page URL
                                log.info("{}: Navigating directly to TEST_URL: {}", threadName, testUrl);
                                WebDriver driver = session.getWebDriver();
                                
                                // Log current URL before navigation
                                String currentUrl = driver.getCurrentUrl();
                                log.info("{}: Current URL before navigation: {}", threadName, currentUrl);
                                
                                // Direct navigation to test page URL instead of using navigator
                                driver.get(testUrl);
                                
                                // Log URL after navigation and check what page we landed on
                                String afterNavigationUrl = driver.getCurrentUrl();
                                log.info("{}: URL after navigation: {}", threadName, afterNavigationUrl);
                                
                                // Log page title
                                String pageTitle = driver.getTitle();
                                log.info("{}: Page title after navigation: {}", threadName, pageTitle);
                                
                                // Check where we landed after navigation attempt
                                if (afterNavigationUrl.equals(testUrl)) {
                                    // We're on the test page - authentication is valid
                                    log.info("{}: Successfully navigated to test page with valid authentication", threadName);
                                    log.info("{}: Looking for test attempt button", threadName);
                                    // Proceed to looking for the test attempt button
                                } else if (afterNavigationUrl.contains(loginUrl)) {
                                    // We're on the login page - authentication was lost but that's expected
                                    log.info("{}: Redirected to login page - will authenticate", threadName);
                                    
                                    // Use the standard authentication process
                                    boolean authSuccess = navigator.authenticate(session);
                                    if (authSuccess) {
                                        log.info("{}: Successfully re-authenticated", threadName);
                                        boolean navSuccess = navigator.navigateToTestPage(session);
                                        if (!navSuccess) {
                                            log.warn("{}: Failed to navigate to test page after re-authentication - will create new session", threadName);
                                            session.close();
                                            session = null;
                                        } else {
                                            log.info("{}: Successfully restored session for next iteration", threadName);
                                        }
                                    } else {
                                        log.warn("{}: Failed to re-authenticate - will create new session", threadName);
                                        session.close();
                                        session = null;
                                    }
                                } else {
                                    // We landed on an unexpected page - log the error and create a new session
                                    log.error("{}: Landed on unexpected URL after navigation: {}", threadName, afterNavigationUrl);
                                    log.warn("{}: Navigation error - will create new session", threadName);
                                    session.close();
                                    session = null;
                                }
                            } catch (Exception e) {
                                log.warn("{}: Error during navigation to test page: {}", threadName, e.getMessage());
                                log.warn("{}: Will create new session", threadName);
                                session.close();
                                session = null;
                            }
                        }
                    } else {
                        log.warn("{}: Iteration failed, will retry with new session", threadName);
                        // Close the failed session and create a new one on next iteration
                        if (session != null) {
                            try {
                                session.close();
                            } catch (Exception e) {
                                log.warn("{}: Error closing failed session: {}", threadName, e.getMessage());
                            }
                            session = null;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("{}: Unexpected error in navigation task: {}", threadName, e.getMessage(), e);
            } finally {
                // Ensure the session is closed when we're done
                if (session != null) {
                    try {
                        session.close();
                        log.info("{}: Closed navigation session", threadName);
                    } catch (Exception e) {
                        log.warn("{}: Error closing session: {}", threadName, e.getMessage());
                    }
                }
                log.info("{}: Navigation task completed", threadName);
            }
            
            return true;
        }
        
        /**
         * Run one iteration of the parsing process
         * @param session The navigation session to use
         * @return true if the iteration was successful, false otherwise
         */
        private boolean runOneIteration(NavigationSession session) {
            String username = session.getAccount().getUsername();
            WebDriver driver = session.getWebDriver();
            
            // Add iteration timeout tracking
            final long startTime = System.currentTimeMillis();
            final long ITERATION_TIMEOUT_MS = 90000; // 90 seconds timeout
            
            // Helper method to check if iteration timeout exceeded
            Supplier<Boolean> isTimeoutExceeded = () -> {
                long elapsedTime = System.currentTimeMillis() - startTime;
                boolean timeoutExceeded = elapsedTime > ITERATION_TIMEOUT_MS;
                
                if (timeoutExceeded) {
                    log.error("{}: Iteration timeout exceeded ({}ms). Terminating iteration for account: {}", 
                        threadName, elapsedTime, username);
                }
                
                return timeoutExceeded;
            };
            
            try {
                // Step 1: Authenticate
                try {
                    boolean authSuccess = navigator.authenticate(session);
                    // Check timeout after authentication
                    if (isTimeoutExceeded.get()) return false;
                    
                    if (!authSuccess) {
                        log.error("{}: Authentication failed for account: {}", threadName, username);
                        return false;
                    }
                    log.info("{}: Authentication successful for account: {}", threadName, username);
                } catch (Exception e) {
                    log.error("{}: Authentication failed for account {}: {}", threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after authentication
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Check timeout before proceeding
                if (isTimeoutExceeded.get()) return false;
                
                // Step 2: Navigate to test page
                try {
                    boolean navSuccess = navigator.navigateToTestPage(session);
                    // Check timeout after navigation
                    if (isTimeoutExceeded.get()) return false;
                    
                    if (!navSuccess) {
                        log.error("{}: Failed to navigate to test page for account: {}", threadName, username);
                        return false;
                    }
                    log.info("{}: Navigation to test page successful for account: {}", threadName, username);
                } catch (Exception e) {
                    log.error("{}: Navigation to test page failed for account {}: {}", threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after navigation to test page
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Check timeout before proceeding
                if (isTimeoutExceeded.get()) return false;
                
                // Step 3: Click "Attempt Test" or "Continue Attempt" button
                TestCenterNavigator.AttemptButtonResult attemptResult;
                try {
                    attemptResult = navigator.clickAttemptTestButton(session);
                    if (!attemptResult.isSuccess()) {
                        log.error("{}: Failed to click test attempt button for account: {}", threadName, username);
                        return false;
                    }
                    log.info("{}: Click on test attempt button successful for account: {}", threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during test attempt button click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after clicking test attempt button
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Step 4: Click "Start Attempt" button - ONLY for new attempts, skip for resume attempts
                if (!attemptResult.isResumeAttempt()) {
                    log.info("{}: New attempt detected, proceeding with 'Start Attempt' button click for account: {}", 
                            threadName, username);
                    try {
                        boolean startBtnSuccess = navigator.clickStartAttemptButton(session);
                        if (!startBtnSuccess) {
                            log.error("{}: Failed to click 'Start Attempt' button for account: {}", 
                                    threadName, username);
                            return false;
                        }
                        log.info("{}: Click on 'Start Attempt' button successful for account: {}", 
                                threadName, username);
                    } catch (Exception e) {
                        log.error("{}: Error during 'Start Attempt' button click for account {}: {}", 
                                threadName, username, e.getMessage());
                        return false;
                    }
                    
                    try {
                        // Short delay after clicking "Start Attempt" button
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.info("{}: Resume attempt detected, skipping 'Start Attempt' button click for account: {}", 
                            threadName, username);
                }
                
                // Step 5: Click "Finish Attempt..." link
                try {
                    boolean finishLinkSuccess = navigator.clickFinishAttemptLink(session);
                    if (!finishLinkSuccess) {
                        log.error("{}: Failed to click 'Finish Attempt...' link for account: {}", 
                                threadName, username);
                        return false;
                    }
                    log.info("{}: Click on 'Finish Attempt...' link successful for account: {}", 
                            threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during 'Finish Attempt...' link click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after clicking "Finish Attempt..." link
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Step 6: Click "Submit All and Finish" button
                try {
                    boolean submitBtnSuccess = navigator.clickSubmitAllButton(session);
                    if (!submitBtnSuccess) {
                        log.error("{}: Failed to click 'Submit All and Finish' button for account: {}", 
                                threadName, username);
                        return false;
                    }
                    log.info("{}: Click on 'Submit All and Finish' button successful for account: {}", 
                            threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during 'Submit All and Finish' button click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after clicking "Submit All and Finish" button
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Step 7: Click confirmation "Submit All and Finish" button
                try {
                    boolean confirmBtnSuccess = navigator.clickConfirmSubmitButton(session);
                    if (!confirmBtnSuccess) {
                        log.error("{}: Failed to click confirmation 'Submit All and Finish' button for account: {}", 
                                threadName, username);
                        return false;
                    }
                    log.info("{}: Click on confirmation 'Submit All and Finish' button successful for account: {}", 
                            threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during confirm 'Submit All and Finish' button click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                // Log only the final URL after completing all steps
                log.info("{}: FINAL RESULT URL for account {}: {}", 
                        threadName, username, session.getCurrentUrl());
                
                // Process the test results
                log.info("{}: Checking for result page URLs", threadName);
                List<String> resultPageUrls = session.getResultPageUrls();
                if (resultPageUrls != null && !resultPageUrls.isEmpty()) {
                    log.info("{}: Found {} result page URLs to process for account: {}", 
                            threadName, resultPageUrls.size(), username);
                    
                    for (String resultPageUrl : resultPageUrls) {
                        try {
                            log.info("{}: Processing result page URL: {}", threadName, resultPageUrl);
                            boolean parseSuccess = testParsingRunner.processTestUrl(resultPageUrl, session);
                            if (parseSuccess) {
                                log.info("{}: Successfully parsed result page: {}", threadName, resultPageUrl);
                            } else {
                                log.warn("{}: Failed to parse result page: {}", threadName, resultPageUrl);
                            }
                        } catch (Exception e) {
                            log.error("{}: Error during parsing of result page {}: {}", 
                                    threadName, resultPageUrl, e.getMessage(), e);
                        }
                    }
                } else {
                    log.warn("{}: Cannot generate result page URLs - no attempt ID available for account: {}", 
                            threadName, username);
                }
                
                log.info("{}: Successfully completed navigation for account: {}", threadName, username);
                
                // Keep the browser open for demonstration purposes
                Thread.sleep(properties.getNavigation().getThreadTimeoutSeconds() * 1000L);
                
                return true;
            } catch (InterruptedException e) {
                log.warn("{}: Navigation task interrupted: {}", threadName, e.getMessage());
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.error("{}: Unexpected error in navigation task: {}", threadName, e.getMessage(), e);
                return false;
            }
        }
    }
} 