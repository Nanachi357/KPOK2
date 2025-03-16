package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Service for managing navigation processes.
 * Supports multi-threading for parallel navigation sessions.
 */
@Slf4j
@Service
public class NavigationService {

    private final NavigationSessionFactory sessionFactory;
    private final TestCenterNavigator navigator;
    private final TestCenterProperties properties;
    
    private ExecutorService executorService;
    private final List<Future<?>> runningTasks = new ArrayList<>();

    @Autowired
    public NavigationService(
            NavigationSessionFactory sessionFactory,
            TestCenterNavigator navigator,
            TestCenterProperties properties) {
        this.sessionFactory = sessionFactory;
        this.navigator = navigator;
        this.properties = properties;
    }
    
    /**
     * Initialize the navigation process, creating sessions for all accounts.
     * This method will authenticate each account and navigate to the test page.
     * 
     * @return true if at least one session was successfully initialized
     */
    public boolean initializeNavigation() {
        int threadCount = properties.getNavigation().getMaxThreads();
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