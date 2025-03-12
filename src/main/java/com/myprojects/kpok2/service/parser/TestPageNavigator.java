package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Component responsible for navigating between test pages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestPageNavigator {

    private final WebDriver webDriver;
    
    // Flag to control HTML file saving
    private static final boolean SAVE_HTML_FILES = false;

    /**
     * Navigate to a test URL
     * @param url URL to navigate to
     * @return true if navigation was successful, false otherwise
     */
    public boolean navigateToTestUrl(String url) {
        try {
            log.info("Navigating to test URL: {}", url);
            webDriver.get(url);
            
            // Wait for the page to load
            log.info("Waiting for page content to load...");
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div#page-content")));
            
            log.info("Navigation successful!");
            log.info("Current URL: {}", webDriver.getCurrentUrl());
            log.info("Page title: {}", webDriver.getTitle());
            
            return true;
        } catch (Exception e) {
            log.error("Navigation failed! Error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Navigate to the next page if available
     * @return true if navigation to next page was successful, false if there's no next page
     */
    public boolean navigateToNextPage() {
        try {
            WebElement nextPageButton = webDriver.findElement(By.cssSelector(TestParserConstants.NEXT_PAGE_SELECTOR));
            if (nextPageButton.isDisplayed() && nextPageButton.isEnabled()) {
                log.info("Found 'Next page' button. Navigating to next page...");
                nextPageButton.click();
                
                // Wait for the new page to load
                WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div#page-content")));
                
                log.info("Successfully navigated to next page.");
                return true;
            } else {
                log.info("Next page button is not available or disabled.");
                return false;
            }
        } catch (NoSuchElementException e) {
            log.info("No 'Next page' button found. This is the last page.");
            return false;
        } catch (Exception e) {
            log.warn("Error while trying to navigate to next page: {}", e.getMessage());
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
        if (SAVE_HTML_FILES) {
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
                log.info("Screenshot taken, size: {} bytes", screenshot.length);
            }
        } catch (Exception e) {
            log.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }
    
    /**
     * Saves the HTML content to a file for debugging
     */
    private void saveHtmlToFile(String html, String fileName) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fullFileName = fileName + "_" + timestamp + ".html";
            
            File outputDir = new File("html_output");
            if (!outputDir.exists()) {
                outputDir.mkdir();
            }
            
            File outputFile = new File(outputDir, fullFileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(html.getBytes());
            }
            
            log.info("HTML saved to file: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to save HTML to file: {}", e.getMessage());
        }
    }
} 