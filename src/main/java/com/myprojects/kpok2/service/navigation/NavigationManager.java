package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.parser.TestParsingRunner;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NavigationManager {
    private final TestCenterProperties properties;
    private final TestCenterNavigator navigator;
    private final NavigationSessionFactory sessionFactory;
    private final TestParsingRunner testParsingRunner;
    private final TestParsingStatistics parsingStatistics;
    private final AccountConfigurationService accountService;
    private final AtomicBoolean isRunning;
    private CompletableFuture<Void> navigationFuture;
    private final NavigationService navigationService;

    @Autowired
    public NavigationManager(
            TestCenterProperties properties,
            TestCenterNavigator navigator,
            NavigationSessionFactory sessionFactory,
            TestParsingRunner testParsingRunner,
            TestParsingStatistics parsingStatistics,
            AccountConfigurationService accountService,
            NavigationService navigationService
    ) {
        this.properties = properties;
        this.navigator = navigator;
        this.sessionFactory = sessionFactory;
        this.testParsingRunner = testParsingRunner;
        this.parsingStatistics = parsingStatistics;
        this.accountService = accountService;
        this.isRunning = new AtomicBoolean(false);
        this.navigationService = navigationService;
    }

    public boolean startNavigation() {
        if (isRunning.get()) {
            log.warn("Navigation is already running");
            return false;
        }

        try {
            log.info("Starting navigation process...");
            parsingStatistics.resetForceStop();
            boolean success = navigationService.startNavigation();

            if (success) {
                isRunning.set(true);
                log.info("Navigation process started successfully");
                
                // Launch process in a separate thread
                navigationFuture = CompletableFuture.runAsync(() -> {
                    try {
                        while (isRunning.get()) {
                            Thread.sleep(1000); // Check state every second
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                
                return true;
            } else {
                log.error("Failed to start navigation process");
                return false;
            }
        } catch (Exception e) {
            log.error("Error starting navigation: {}", e.getMessage(), e);
            return false;
        }
    }

    public void stopNavigation() {
        if (!isRunning.get()) {
            log.warn("Navigation is not running");
            return;
        }

        log.info("Stopping navigation process...");
        isRunning.set(false);
        parsingStatistics.forceStop();
        
        if (navigationFuture != null) {
            navigationFuture.cancel(true);
            navigationFuture = null;
        }
        
        // Interrupt all running threads
        navigationService.shutdown();
        
        // Wait for threads to finish
        try {
            if (!navigationService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Some threads did not terminate in time, forcing shutdown");
                navigationService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for threads to terminate", e);
            Thread.currentThread().interrupt();
        }
        
        log.info("Navigation process stopped");
    }

    public boolean isRunning() {
        return isRunning.get();
    }
} 