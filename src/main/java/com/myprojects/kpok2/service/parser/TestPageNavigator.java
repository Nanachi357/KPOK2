package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.config.DebugProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.openqa.selenium.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Component responsible for test page content access and verification
 */
@Slf4j
@Component
public class TestPageNavigator {

    private final DebugProperties debugProperties;
    private WebDriver webDriver;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    
    @Autowired
    public TestPageNavigator(DebugProperties debugProperties) {
        this.debugProperties = debugProperties;
    }
    
    @PostConstruct
    public void init() {
        if (debugProperties.isSaveFiles()) {
            log.info("Cleaning up debug directories...");
            try {
                cleanupDebugDirs();
                log.info("Debug directories cleaned successfully");
            } catch (IOException e) {
                log.error("Failed to cleanup debug directories: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Clean up debug directories at startup
     */
    private void cleanDebugDirectory() {
        try {
            log.info("Cleaning up debug directories...");
            
            // Create or clean directories
            createOrCleanDirectory(debugProperties.getHtmlDirPath());
            createOrCleanDirectory(debugProperties.getScreenshotsDirPath());
            
            log.info("Debug directories cleaned successfully");
        } catch (IOException e) {
            log.error("Failed to clean debug directories: {}", e.getMessage());
        }
    }
    
    /**
     * Create directory if it doesn't exist or clean it if it does
     * @param dirPath path to directory
     */
    private void createOrCleanDirectory(String dirPath) throws IOException {
        Path path = Path.of(dirPath);
        if (Files.exists(path)) {
            // Delete all files in the directory
            Files.walk(path)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        log.warn("Failed to delete file {}: {}", file, e.getMessage());
                    }
                });
            log.debug("Cleaned directory: {}", dirPath);
        } else {
            // Create a new directory
            Files.createDirectories(path);
            log.debug("Created directory: {}", dirPath);
        }
    }
    
    /**
     * Set the WebDriver instance to use
     * @param webDriver WebDriver instance from the navigation session
     */
    public void setWebDriver(WebDriver webDriver) {
        this.webDriver = webDriver;
    }
    
    /**
     * Verify that the page was loaded successfully and we are on the expected URL
     * @param expectedUrl URL that we expected to navigate to
     * @return true if page loaded successfully and URL matches, false otherwise
     */
    public boolean verifyPageLoaded(String expectedUrl) {
        try {
            // Check URL
            String currentUrl = webDriver.getCurrentUrl();
            if (!currentUrl.equals(expectedUrl)) {
                // If URL doesn't match, check if we were redirected to a login/error page
                if (currentUrl.contains("/login/") || 
                    currentUrl.contains("/error/") || 
                    currentUrl.contains("/?redirect=")) {
                    log.error("Redirected to auth/error page instead of target URL. Current: {}, Expected: {}", 
                             currentUrl, expectedUrl);
                    return false;
                }
                log.warn("Current URL doesn't match expected. Current: {}, Expected: {}", 
                         currentUrl, expectedUrl);
                return false;
            }

            // Check for content presence
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(5));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div#page-content")));
            
            return true;
        } catch (Exception e) {
            log.error("Failed to verify page load: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if we are still authenticated
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        try {
            // Check for user menu presence
            return webDriver.findElements(By.cssSelector("li.nav-item.dropdown.ml-3.usermenu")).size() > 0;
        } catch (Exception e) {
            log.error("Failed to check authentication status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the current page as a JSoup Document
     * @return JSoup Document of the current page
     */
    public Document getCurrentPageDocument() {
        String pageSource = webDriver.getPageSource();
        
        // Save the HTML to a file for debugging if needed
        if (debugProperties.isSaveFiles()) {
            saveHtmlToFile(pageSource, "page_" + System.currentTimeMillis());
        }
        
        return Jsoup.parse(pageSource);
    }
    
    /**
     * Take a screenshot of the current page
     */
    public void takeScreenshot() {
        try {
            if (webDriver instanceof org.openqa.selenium.TakesScreenshot) {
                log.info("Taking screenshot of the page...");
                org.openqa.selenium.OutputType<byte[]> outputType = org.openqa.selenium.OutputType.BYTES;
                byte[] screenshot = ((org.openqa.selenium.TakesScreenshot) webDriver).getScreenshotAs(outputType);
                
                if (debugProperties.isSaveFiles()) {
                    saveScreenshot(screenshot);
                }
                
                log.info("Screenshot taken, size: {} bytes", screenshot.length);
            }
        } catch (Exception e) {
            log.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }
    
    /**
     * Save screenshot to file
     */
    private void saveScreenshot(byte[] screenshot) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("screenshot_%s.png", timestamp);
            
            Path filePath = Path.of(debugProperties.getScreenshotsDirPath(), fileName);
            Files.write(filePath, screenshot);
            
            log.info("Screenshot saved to: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to save screenshot: {}", e.getMessage());
        }
    }
    
    /**
     * Saves the HTML content to a file for debugging
     */
    private void saveHtmlToFile(String html, String fileName) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fullFileName = fileName + "_" + timestamp + ".html";
            
            Path filePath = Path.of(debugProperties.getHtmlDirPath(), fullFileName);
            Files.writeString(filePath, html);
            
            log.info("HTML saved to: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to save HTML to file: {}", e.getMessage());
        }
    }
    
    /**
     * Navigate to test URL and verify the page is loaded
     */
    public boolean navigateToTestUrl(WebDriver driver, String url) {
        try {
            driver.get(url);
            
            // Wait for page content to load
            WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
            
            // Verify URL
            String currentUrl = driver.getCurrentUrl();
            if (!currentUrl.equals(url)) {
                log.error("Wrong page loaded. Expected: {}, Got: {}", url, currentUrl);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error navigating to URL {}: {}", url, e.getMessage());
            return false;
        }
    }
    
    /**
     * Save page source to debug directory
     */
    public void savePageSource(WebDriver driver, String url) {
        try {
            String pageSource = driver.getPageSource();
            String fileName = generateFileName(url, "html");
            Path filePath = Paths.get(debugProperties.getHtmlDirPath(), fileName);
            Files.writeString(filePath, pageSource);
            log.debug("Saved page source to: {}", filePath);
        } catch (Exception e) {
            log.error("Error saving page source for URL {}: {}", url, e.getMessage());
        }
    }
    
    /**
     * Save screenshot to debug directory
     */
    public void saveScreenshot(WebDriver driver, String url) {
        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            File screenshot = ts.getScreenshotAs(OutputType.FILE);
            String fileName = generateFileName(url, "png");
            Path filePath = Paths.get(debugProperties.getScreenshotsDirPath(), fileName);
            Files.copy(screenshot.toPath(), filePath);
            log.debug("Saved screenshot to: {}", filePath);
        } catch (Exception e) {
            log.error("Error saving screenshot for URL {}: {}", url, e.getMessage());
        }
    }
    
    private void cleanupDebugDirs() throws IOException {
        // Create or clean HTML directory
        Path htmlDir = Paths.get(debugProperties.getHtmlDirPath());
        Files.createDirectories(htmlDir);
        log.debug("Created directory: {}", htmlDir);
        
        // Create or clean screenshots directory
        Path screenshotsDir = Paths.get(debugProperties.getScreenshotsDirPath());
        Files.createDirectories(screenshotsDir);
        log.debug("Created directory: {}", screenshotsDir);
    }
    
    private String generateFileName(String url, String extension) {
        // Remove protocol and special characters, replace with underscores
        String fileName = url.replaceAll("https?://", "")
                .replaceAll("[^a-zA-Z0-9.]", "_");
        
        // Add timestamp to make filename unique
        return String.format("%d_%s.%s", System.currentTimeMillis(), fileName, extension);
    }
} 