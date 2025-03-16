package com.myprojects.kpok2.service.navigation;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing WebDriver instances in a multithreaded environment.
 * Each thread will use its own WebDriver instance.
 */
@Slf4j
@Component
public class WebDriverFactory {
    
    private final ConcurrentHashMap<Long, WebDriver> driverMap = new ConcurrentHashMap<>();
    
    /**
     * Get a WebDriver instance for the current thread.
     * If the thread doesn't have a driver yet, a new one will be created.
     *
     * @return WebDriver instance for this thread
     */
    public WebDriver getDriver() {
        long threadId = Thread.currentThread().getId();
        return driverMap.computeIfAbsent(threadId, id -> {
            log.info("Creating new WebDriver for thread {}", id);
            return createWebDriver();
        });
    }
    
    /**
     * Close the WebDriver for the current thread and remove it from the map.
     */
    public void closeDriver() {
        long threadId = Thread.currentThread().getId();
        WebDriver driver = driverMap.remove(threadId);
        if (driver != null) {
            try {
                log.info("Closing WebDriver for thread {}", threadId);
                driver.quit();
            } catch (Exception e) {
                log.warn("Error closing WebDriver: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Close all WebDriver instances.
     * This method should be called during application shutdown.
     */
    public void closeAllDrivers() {
        log.info("Closing all WebDriver instances ({})", driverMap.size());
        for (WebDriver driver : driverMap.values()) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Error closing WebDriver: {}", e.getMessage());
            }
        }
        driverMap.clear();
    }
    
    /**
     * Create a new WebDriver instance with appropriate configuration.
     *
     * @return New WebDriver instance
     */
    private WebDriver createWebDriver() {
        ChromeOptions options = new ChromeOptions();
        
        // Configure Chrome for optimal automation
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        
        // Disable images and CSS to improve performance (optional)
        // options.addArguments("--disable-images");
        
        // Each browser should have a separate user data directory
        // to ensure session isolation between threads
        options.addArguments("--user-data-dir=/tmp/chrome-profile-" + Thread.currentThread().getId());
        
        // Create and return the WebDriver
        return new ChromeDriver(options);
    }
} 