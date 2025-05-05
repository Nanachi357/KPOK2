package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TimeoutSettings;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing WebDriver instances.
 */
@Slf4j
@Component
public class WebDriverFactory {
    // Map to store WebDriver instances by thread ID
    private final ConcurrentHashMap<Long, WebDriver> driverMap = new ConcurrentHashMap<>();
    
    private final TimeoutSettings timeoutSettings;
    
    public WebDriverFactory(TimeoutSettings timeoutSettings) {
        this.timeoutSettings = timeoutSettings;
    }
    
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
        WebDriver driver = driverMap.computeIfAbsent(threadId, k -> createDriver());
        
        return driver;
    }
    
    /**
     * Close the WebDriver instance for the current thread.
     * This method can be called safely even if no WebDriver exists.
     */
    public void quitDriver() {
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
                log.error("Error closing WebDriver for thread {}: {}", threadId, e.getMessage());
            }
        }
    }
    
    /**
     * Close all WebDriver instances across all threads.
     * This should be called during application shutdown.
     */
    public void closeAllDrivers() {
        log.info("Closing all WebDriver instances ({})", driverMap.size());
        for (ConcurrentHashMap.Entry<Long, WebDriver> entry : driverMap.entrySet()) {
            try {
                entry.getValue().quit();
                log.debug("Closed WebDriver for thread {}", entry.getKey());
            } catch (Exception e) {
                log.warn("Error closing WebDriver for thread {}: {}", entry.getKey(), e.getMessage());
            }
        }
        driverMap.clear();
        log.info("All WebDriver instances closed");
    }
    
    /**
     * Create a new WebDriver instance with appropriate configuration.
     *
     * @return New WebDriver instance
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        
        // Create a new ChromeDriver instance with the configured options
        WebDriver driver = new ChromeDriver(options);
        
        // Configure timeouts using the current preset
        driver.manage().timeouts()
              .pageLoadTimeout(timeoutSettings.getPageLoadTimeout())
              .scriptTimeout(timeoutSettings.getScriptTimeout())
              .implicitlyWait(timeoutSettings.getImplicitWait());
        
        log.info("Created new WebDriver instance for thread {} with timeouts: pageLoad={}, script={}, implicit={}", 
                Thread.currentThread().getId(), 
                timeoutSettings.getPageLoadTimeout(),
                timeoutSettings.getScriptTimeout(),
                timeoutSettings.getImplicitWait());
        
        return driver;
    }
} 