package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.parser.TestParsingRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
            
            try (NavigationSession session = sessionFactory.createSession()) {
                log.info("{}: Acquired session with account: {}", 
                        threadName, session.getAccount().getUsername());
                
                String username = session.getAccount().getUsername();
                WebDriver driver = session.getWebDriver();
                
                // Step 1: Authenticate
                try {
                    boolean authSuccess = navigator.authenticate(session);
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
                
                // Step 2: Navigate to test page
                try {
                    boolean navSuccess = navigator.navigateToTestPage(session);
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
                
                // Step 3: Click "Спроба тесту" or "Продовжити спробу" button
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
                
                // Step 4: Click "Почати спробу" button - only if we clicked "Спроба тесту" (not "Продовжити спробу")
                if (!attemptResult.isResumeAttempt()) {
                    try {
                        boolean startBtnSuccess = navigator.clickStartAttemptButton(session);
                        if (!startBtnSuccess) {
                            log.error("{}: Failed to click 'Почати спробу' button for account: {}", 
                                    threadName, username);
                            return false;
                        }
                        log.info("{}: Click on 'Почати спробу' button successful for account: {}", 
                                threadName, username);
                    } catch (Exception e) {
                        log.error("{}: Error during 'Почати спробу' button click for account {}: {}", 
                                threadName, username, e.getMessage());
                        return false;
                    }
                    
                    try {
                        // Short delay after clicking "Почати спробу" button
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.info("{}: Skipping 'Почати спробу' button click for account: {} as this is a resume attempt", 
                            threadName, username);
                }
                
                // Step 5: Click "Завершити спробу..." link
                try {
                    boolean finishLinkSuccess = navigator.clickFinishAttemptLink(session);
                    if (!finishLinkSuccess) {
                        log.error("{}: Failed to click 'Завершити спробу...' link for account: {}", 
                                threadName, username);
                        return false;
                    }
                    log.info("{}: Click on 'Завершити спробу...' link successful for account: {}", 
                            threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during 'Завершити спробу...' link click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after clicking "Завершити спробу..." link
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Step 6: Click "Відправити все та завершити" button
                try {
                    boolean submitBtnSuccess = navigator.clickSubmitAllButton(session);
                    if (!submitBtnSuccess) {
                        log.error("{}: Failed to click 'Відправити все та завершити' button for account: {}", 
                                threadName, username);
                        return false;
                    }
                    log.info("{}: Click on 'Відправити все та завершити' button successful for account: {}", 
                            threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during 'Відправити все та завершити' button click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                try {
                    // Short delay after clicking "Відправити все та завершити" button
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Step 7: Click confirmation "Відправити все та завершити" button
                try {
                    boolean confirmBtnSuccess = navigator.clickConfirmSubmitButton(session);
                    if (!confirmBtnSuccess) {
                        log.error("{}: Failed to click confirmation 'Відправити все та завершити' button for account: {}", 
                                threadName, username);
                        return false;
                    }
                    log.info("{}: Click on confirmation 'Відправити все та завершити' button successful for account: {}", 
                            threadName, username);
                } catch (Exception e) {
                    log.error("{}: Error during confirm 'Відправити все та завершити' button click for account {}: {}", 
                            threadName, username, e.getMessage());
                    return false;
                }
                
                // Log only the final URL after completing all steps
                log.info("{}: FINAL RESULT URL for account {}: {}", 
                        threadName, username, session.getCurrentUrl());
                
                // Generate and log URLs for all result pages
                if (session.hasActiveAttempt()) {
                    String baseResultUrl = "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=" + 
                                          session.getAttemptId() + "&cmid=109";
                    String page1Url = baseResultUrl + "&page=1";
                    String page2Url = baseResultUrl + "&page=2";
                    
                    // Create a list of result page URLs
                    List<String> resultPageUrls = java.util.List.of(baseResultUrl, page1Url, page2Url);
                    
                    // Store URLs in session
                    session.setResultPageUrls(resultPageUrls);
                    
                    // Log all result page URLs
                    log.info("{}: Result page URLs for account {}:", threadName, username);
                    log.info("{}: Base Result URL: {}", threadName, baseResultUrl);
                    log.info("{}: Page 1 URL: {}", threadName, page1Url);
                    log.info("{}: Page 2 URL: {}", threadName, page2Url);
                    
                    // Parse the result pages using the same WebDriver session
                    log.info("{}: Starting parsing of result pages for account {}", threadName, username);
                    
                    // Process each result page URL
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
            } finally {
                log.info("{}: Navigation task completed", threadName);
            }
        }
    }
} 