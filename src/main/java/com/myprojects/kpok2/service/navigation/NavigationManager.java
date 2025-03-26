package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.parser.TestParsingRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class NavigationManager {
    private final TestCenterProperties properties;
    private final TestCenterNavigator navigator;
    private final NavigationSessionFactory sessionFactory;
    private final TestParsingRunner testParsingRunner;
    private final AtomicBoolean isRunning;
    private CompletableFuture<Void> navigationFuture;

    public NavigationManager(
            TestCenterProperties properties,
            TestCenterNavigator navigator,
            NavigationSessionFactory sessionFactory,
            TestParsingRunner testParsingRunner
    ) {
        this.properties = properties;
        this.navigator = navigator;
        this.sessionFactory = sessionFactory;
        this.testParsingRunner = testParsingRunner;
        this.isRunning = new AtomicBoolean(false);
    }

    public boolean startNavigation() {
        if (isRunning.get()) {
            log.warn("Navigation is already running");
            return false;
        }

        try {
            NavigationService navigationService = new NavigationService(
                    properties,
                    navigator,
                    sessionFactory,
                    testParsingRunner
            );

            log.info("Starting navigation process...");
            boolean success = navigationService.startNavigation();

            if (success) {
                isRunning.set(true);
                log.info("Navigation process started successfully");
                
                // Запускаємо процес в окремому потоці
                navigationFuture = CompletableFuture.runAsync(() -> {
                    try {
                        while (isRunning.get()) {
                            Thread.sleep(1000); // Перевіряємо стан кожну секунду
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
        
        if (navigationFuture != null) {
            navigationFuture.cancel(true);
            navigationFuture = null;
        }
        
        // TODO: Додати коректне завершення роботи браузерів
        log.info("Navigation process stopped");
    }

    public boolean isRunning() {
        return isRunning.get();
    }
} 