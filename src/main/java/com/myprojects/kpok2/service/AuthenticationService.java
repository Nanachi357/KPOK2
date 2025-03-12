package com.myprojects.kpok2.service;

import com.myprojects.kpok2.config.TestCenterConfig;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final WebDriver webDriver;
    private final TestCenterConfig config;

    /**
     * Attempt to log in to the test center if not already logged in
     * @return true if login successful or already logged in, false otherwise
     */
    public boolean login() {
        try {
            log.info("Checking authentication status...");
            
            // Check if already logged in before attempting to log in again
            if (isLoggedIn()) {
                log.info("Already logged in, skipping authentication process");
                return true;
            }
            
            log.info("Not logged in. Starting authentication process...");
            log.debug("Opening login page: {}", config.getLoginUrl());
            webDriver.get(config.getLoginUrl());

            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));

            // Check if we were redirected to a different page (might be already logged in)
            if (isLoggedIn()) {
                log.info("Redirected to logged-in state after opening login page");
                return true;
            }

            log.debug("Current URL before login: {}", webDriver.getCurrentUrl());
            
            // Check if login form elements exist before trying to find them
            if (!areLoginFormElementsPresent()) {
                log.warn("Login form elements not found. Checking if already logged in again...");
                if (isLoggedIn()) {
                    log.info("Already logged in, despite login form not being visible");
                    return true;
                }
                log.error("Login form elements not found and not logged in. Cannot authenticate.");
                return false;
            }
            
            log.debug("Looking for username input with selector: {}", TestParserConstants.USERNAME_SELECTOR);
            WebElement usernameInput = webDriver.findElement(By.cssSelector(TestParserConstants.USERNAME_SELECTOR));

            log.debug("Looking for password input with selector: {}", TestParserConstants.PASSWORD_SELECTOR);
            WebElement passwordInput = webDriver.findElement(By.cssSelector(TestParserConstants.PASSWORD_SELECTOR));

            log.debug("Looking for login button with selector: {}", TestParserConstants.LOGIN_BUTTON_SELECTOR);
            WebElement submitButton = webDriver.findElement(By.cssSelector(TestParserConstants.LOGIN_BUTTON_SELECTOR));

            log.debug("Found all form elements, proceeding with login");
            log.debug("Entering username: {}", config.getUsername());
            usernameInput.sendKeys(config.getUsername());

            log.debug("Entering password: [MASKED]");
            passwordInput.sendKeys(config.getPassword());

            log.debug("Clicking login button");
            submitButton.click();

            log.debug("Waiting for redirect to personal cabinet...");
            wait.until(ExpectedConditions.or(
                ExpectedConditions.urlToBe("https://test.testcentr.org.ua/my/"),
                ExpectedConditions.urlContains("/?redirect=0"),
                ExpectedConditions.urlContains("/user/profile.php")
            ));

            log.debug("Current URL after login: {}", webDriver.getCurrentUrl());
            log.info("Authentication successful!");

            return true;
        } catch (Exception e) {
            log.error("Login failed! Error: {}", e.getMessage());
            log.error("Current URL at failure: {}", webDriver.getCurrentUrl());
            log.error("Stack trace:", e);
            return false;
        }
    }
    
    /**
     * Check if the user is currently logged in
     * @return true if logged in, false otherwise
     */
    public boolean isLoggedIn() {
        try {
            String currentUrl = webDriver.getCurrentUrl();
            
            // Quick URL-based check
            if (currentUrl.contains("/my/") || 
                currentUrl.contains("/?redirect=0") || 
                currentUrl.contains("/user/profile.php")) {
                log.debug("Logged in state detected based on URL: {}", currentUrl);
                return true;
            }
            
            // Check for user menu element (more comprehensive check)
            try {
                // Use a short implicit wait to avoid long timeouts
                webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
                boolean hasUserMenu = webDriver.findElements(By.cssSelector("li.nav-item.dropdown.ml-3.usermenu")).size() > 0;
                boolean hasLogoutLink = webDriver.findElements(By.xpath("//a[contains(@href, '/login/logout.php')]")).size() > 0;
                
                if (hasUserMenu || hasLogoutLink) {
                    log.debug("Logged in state detected based on user interface elements");
                    return true;
                }
                
                return false;
            } finally {
                // Reset implicit wait to avoid affecting other operations
                webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            }
        } catch (Exception e) {
            log.debug("Error while checking login status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if login form elements are present on the page
     * @return true if all elements are present, false otherwise
     */
    private boolean areLoginFormElementsPresent() {
        try {
            // Use a short implicit wait to avoid long timeouts
            webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
            
            try {
                boolean hasUsernameField = webDriver.findElements(By.cssSelector(TestParserConstants.USERNAME_SELECTOR)).size() > 0;
                boolean hasPasswordField = webDriver.findElements(By.cssSelector(TestParserConstants.PASSWORD_SELECTOR)).size() > 0;
                boolean hasLoginButton = webDriver.findElements(By.cssSelector(TestParserConstants.LOGIN_BUTTON_SELECTOR)).size() > 0;
                
                boolean allElementsPresent = hasUsernameField && hasPasswordField && hasLoginButton;
                
                if (!allElementsPresent) {
                    log.debug("Login form elements missing - Username: {}, Password: {}, Button: {}", 
                             hasUsernameField, hasPasswordField, hasLoginButton);
                }
                
                return allElementsPresent;
            } finally {
                // Reset implicit wait to avoid affecting other operations
                webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            }
        } catch (Exception e) {
            log.debug("Error checking login form elements: {}", e.getMessage());
            return false;
        }
    }
}