package com.myprojects.kpok2.service.navigation;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing WebDriver instances.
 */
@Slf4j
@Component
public class WebDriverFactory {
    private static final Path PROFILE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "kpok2-chrome").toAbsolutePath().normalize();

    // Map to store WebDriver instances by thread ID
    private final Map<Long, WebDriver> driverMap = new ConcurrentHashMap<>();
    private final Map<WebDriver, Path> profilePaths = new ConcurrentHashMap<>();
    
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
     * Creates an isolated headless browser for short background operations.
     * The caller owns the returned driver and must close it with
     * {@link #closeDriver(WebDriver)}.
     */
    public WebDriver createHeadlessDriver() {
        WebDriver driver = createWebDriver(true);
        log.info("Created headless WebDriver instance for background operation");
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
            closeDriverAndProfile(driver, "thread " + threadId);
        }
        
        // Remove the current thread's driver
        currentDriver.remove();
    }

    /**
     * Closes a detached browser created for a background operation.
     */
    public void closeDriver(WebDriver driver) {
        if (driver == null) {
            return;
        }

        closeDriverAndProfile(driver, "background operation");
    }
    
    /**
     * Close all WebDriver instances across all threads.
     * This should be called during application shutdown.
     */
    public void closeAllDrivers() {
        log.info("Closing all WebDriver instances ({})", driverMap.size());
        for (Map.Entry<Long, WebDriver> entry : driverMap.entrySet()) {
            closeDriverAndProfile(entry.getValue(), "thread " + entry.getKey());
        }
        driverMap.clear();
        // Detached background drivers are caller-owned during normal work,
        // but application shutdown must still release any that are in flight.
        for (WebDriver driver : List.copyOf(profilePaths.keySet())) {
            closeDriverAndProfile(driver, "application shutdown");
        }
        currentDriver.remove();
        log.info("All WebDriver instances closed");
    }
    
    /**
     * Create a new WebDriver instance with appropriate configuration.
     *
     * @return New WebDriver instance
     */
    private WebDriver createWebDriver() {
        return createWebDriver(false);
    }

    private WebDriver createWebDriver(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        
        // Configure Chrome for optimal automation
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        if (headless) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
        }
        
        // Disable images and CSS to improve performance (optional)
        // options.addArguments("--disable-images");
        
        // Each browser gets a fresh profile. A thread-based path alone can be
        // left locked after a browser crash and block later application runs.
        Path profilePath = createProfilePath(
                Thread.currentThread().getId(), headless, UUID.randomUUID().toString());
        options.addArguments("--user-data-dir=" + profilePath);

        try {
            WebDriver driver = new ChromeDriver(options);
            profilePaths.put(driver, profilePath);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            return driver;
        } catch (RuntimeException e) {
            deleteProfileDirectory(profilePath);
            throw e;
        }
    }

    static Path createProfilePath(long threadId, boolean headless, String uniqueId) {
        String mode = headless ? "-headless" : "";
        return PROFILE_ROOT.resolve(
                "profile-" + threadId + mode + "-" + uniqueId).normalize();
    }

    private void closeDriverAndProfile(WebDriver driver, String owner) {
        Path profilePath = profilePaths.remove(driver);
        try {
            driver.quit();
            log.info("Closed WebDriver instance for {}", owner);
        } catch (Exception e) {
            log.warn("Error closing WebDriver for {}: {}", owner, e.getMessage());
        } finally {
            deleteProfileDirectory(profilePath);
        }
    }

    private static void deleteProfileDirectory(Path profilePath) {
        if (profilePath == null) {
            return;
        }

        Path normalized = profilePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(PROFILE_ROOT) || normalized.equals(PROFILE_ROOT)) {
            log.warn("Refusing to delete unexpected Chrome profile path: {}", normalized);
            return;
        }

        try {
            if (!Files.exists(normalized)) {
                return;
            }
            try (var paths = Files.walk(normalized)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            log.debug("Could not fully delete Chrome profile {}: {}", normalized, e.getMessage());
        }
    }
}
