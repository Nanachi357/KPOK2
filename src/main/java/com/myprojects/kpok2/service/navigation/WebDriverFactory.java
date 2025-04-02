package com.myprojects.kpok2.service.navigation;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing WebDriver instances.
 */
@Slf4j
@Component
public class WebDriverFactory {
    // Map to store WebDriver instances by thread ID
    private final Map<Long, WebDriver> driverMap = new ConcurrentHashMap<>();
    
    // ThreadLocal to track current thread's WebDriver
    private final ThreadLocal<WebDriver> currentDriver = new ThreadLocal<>();
    
    /**
     * Get a WebDriver instance for the current thread.
     * Creates a new instance if none exists.
     *
     * @return The WebDriver instance for the current thread
     */
    public WebDriver getDriver() {
        // Get current thread ID
        long threadId = Thread.currentThread().getId();
        
        // Check if a WebDriver already exists for this thread
        WebDriver driver = driverMap.get(threadId);
        
        // If no WebDriver exists, create a new one
        if (driver == null) {
            driver = createWebDriver();
            driverMap.put(threadId, driver);
            log.info("Created new WebDriver instance for thread {}", threadId);
        } else {
            log.debug("Reusing existing WebDriver instance for thread {}", threadId);
        }
        
        // Set the current thread's driver
        currentDriver.set(driver);
        
        return driver;
    }
    
    /**
     * Close the WebDriver instance for the current thread.
     * This method can be called safely even if no WebDriver exists.
     */
    public void closeDriver() {
        // Get current thread ID
        long threadId = Thread.currentThread().getId();
        
        // Get the WebDriver for this thread
        WebDriver driver = driverMap.remove(threadId);
        
        // If a WebDriver exists, close it
        if (driver != null) {
            try {
                driver.quit();
                log.info("Closed WebDriver instance for thread {}", threadId);
            } catch (Exception e) {
                log.warn("Error closing WebDriver for thread {}: {}", threadId, e.getMessage());
            }
        }
        
        // Remove the current thread's driver
        currentDriver.remove();
    }
    
    /**
     * Close all WebDriver instances across all threads.
     * This should be called during application shutdown.
     */
    public void closeAllDrivers() {
        log.info("Closing all WebDriver instances ({})", driverMap.size());
        for (Map.Entry<Long, WebDriver> entry : driverMap.entrySet()) {
            try {
                entry.getValue().quit();
                log.debug("Closed WebDriver for thread {}", entry.getKey());
            } catch (Exception e) {
                log.warn("Error closing WebDriver for thread {}: {}", entry.getKey(), e.getMessage());
            }
        }
        driverMap.clear();
        currentDriver.remove();
        log.info("All WebDriver instances closed");
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