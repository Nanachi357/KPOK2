package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.model.QuizTarget;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Navigator for TestCenter website.
 * Handles authentication and navigation to test pages.
 */
@Slf4j
@Component
public class TestCenterNavigator {

    private static final String LOGIN_URL = "https://test.testcentr.org.ua/login/index.php";
    
    private static final By USERNAME_SELECTOR = By.cssSelector("input#username");
    private static final By PASSWORD_SELECTOR = By.cssSelector("input#password");
    private static final By LOGIN_BUTTON_SELECTOR = By.cssSelector("button#loginbtn");
    private static final By USER_MENU_SELECTOR = By.cssSelector(
            ".usermenu, [data-region='user-menu']");
    private static final By LOGIN_ERROR_SELECTOR = By.cssSelector(
            ".loginerrors, .alert-danger");
    
    private static final By ATTEMPT_TEST_BUTTON_SELECTOR = By.cssSelector(
            ".singlebutton form[action*='/mod/quiz/'] button[type='submit'], "
                    + ".singlebutton form[action*='/mod/quiz/'] input[type='submit']");
    private static final By START_ATTEMPT_BUTTON_SELECTOR = By.cssSelector(
            "#mod_quiz_preflight_form #id_submitbutton, "
                    + "#mod_quiz_preflight_form button[type='submit'], "
                    + "#mod_quiz_preflight_form input[type='submit']");
    private static final By SUBMIT_ALL_BUTTON_SELECTOR = By.cssSelector(
            ".submitbtns button[type='submit'], .submitbtns input[type='submit']");
    private static final By CONFIRM_SUBMIT_BUTTON_SELECTOR = By.cssSelector("button.btn-primary[data-action='save']");
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    // Adding a new class for attempt button click result
    public class AttemptButtonResult {
        private final boolean success;
        private final boolean isResumeAttempt; // true for "Continue attempt", false for "Test attempt"
        
        public AttemptButtonResult(boolean success, boolean isResumeAttempt) {
            this.success = success;
            this.isResumeAttempt = isResumeAttempt;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isResumeAttempt() {
            return isResumeAttempt;
        }
    }

    public enum AuthenticationStatus {
        SUCCESS,
        INVALID_CREDENTIALS,
        ERROR
    }

    public record AuthenticationResult(AuthenticationStatus status, String message) {
        public boolean isSuccess() {
            return status == AuthenticationStatus.SUCCESS;
        }
    }

    /**
     * Authenticate to the TestCenter website
     *
     * @param session The navigation session containing account credentials and WebDriver
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        AuthenticationResult result = authenticate(driver, session.getAccount());
        if (result.isSuccess()) {
            session.updateUrl(driver.getCurrentUrl());
        }
        return result.isSuccess();
    }

    /**
     * Authenticates explicit credentials without requiring a quiz attempt session.
     * Used by account validation and catalog refresh.
     */
    public AuthenticationResult authenticate(WebDriver driver, AccountCredentials account) {
        String username = account.getUsername();

        try {
            log.info("Navigating to login page for account: {}", username);
            driver.get(LOGIN_URL);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(USER_MENU_SELECTOR),
                    ExpectedConditions.visibilityOfElementLocated(USERNAME_SELECTOR)));

            if (!driver.findElements(USER_MENU_SELECTOR).isEmpty()) {
                log.info("Account {} already has an authenticated session", username);
                return new AuthenticationResult(
                        AuthenticationStatus.SUCCESS, "Authentication successful");
            }

            log.info("Entering credentials for account: {}", username);
            WebElement usernameField = driver.findElement(USERNAME_SELECTOR);
            WebElement passwordField = driver.findElement(PASSWORD_SELECTOR);
            usernameField.clear();
            passwordField.clear();
            usernameField.sendKeys(username);
            passwordField.sendKeys(account.getPassword());
            driver.findElement(LOGIN_BUTTON_SELECTOR).click();

            AuthenticationResult result = waitForAuthenticationResult(driver);
            if (result.isSuccess()) {
                log.info("Authentication successful for account: {}", username);
            } else {
                log.error("Authentication failed for account {}: {}", username, result.message());
            }
            return result;
        } catch (Exception e) {
            log.error("Error during authentication for account {}: {}", username, e.getMessage());
            return new AuthenticationResult(
                    AuthenticationStatus.ERROR,
                    "Could not reach or authenticate with TestCenter: " + e.getMessage());
        }
    }
    
    /**
     * Navigate to the test page
     *
     * @param session The navigation session
     * @return true if navigation was successful, false otherwise
     */
    public boolean navigateToTestPage(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        
        try {
            log.info("Navigating to test page for account: {}", username);
            driver.get(session.getQuizTarget().viewUrl());
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            // Wait for page content to load
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
            
            // Update session URL
            session.updateUrl(driver.getCurrentUrl());
            
            log.info("Successfully navigated to test page for account: {}", username);
            return true;
        } catch (Exception e) {
            log.error("Error navigating to test page for account {}: {}", username, e.getMessage());
            return false;
        }
    }
    
    /**
     * Click the "Test attempt" or "Continue attempt" button on the test page
     *
     * @param session The navigation session
     * @return AttemptButtonResult object containing success status and whether it was a resume attempt
     */
    public AttemptButtonResult clickAttemptTestButton(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        String threadName = Thread.currentThread().getName();
        
        try {
            log.info("{}: Looking for test attempt button for account: {}", threadName, username);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            // Wait for the button to be clickable
            WebElement attemptButton = wait.until(
                    ExpectedConditions.elementToBeClickable(ATTEMPT_TEST_BUTTON_SELECTOR));
            
            log.info("{}: Found quiz attempt form for account: {}", threadName, username);
            
            // Click the button
            attemptButton.click();
            
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("/mod/quiz/attempt.php"),
                    ExpectedConditions.visibilityOfElementLocated(START_ATTEMPT_BUTTON_SELECTOR)));
            
            // Update session URL
            String currentUrl = driver.getCurrentUrl();
            session.updateUrl(currentUrl);
            boolean isResumeAttempt = isAttemptPage(currentUrl);
            
            log.info("{}: Successfully opened quiz attempt flow for account: {}. Resume: {}",
                    threadName, username, isResumeAttempt);
            return new AttemptButtonResult(true, isResumeAttempt);
        } catch (Exception e) {
            log.error("{}: Error clicking test attempt button for account {}: {}", 
                    threadName, username, e.getMessage());
            return new AttemptButtonResult(false, false);
        }
    }
    
    /**
     * Click the "Start attempt" button in the confirmation dialog
     *
     * @param session The navigation session
     * @return true if button click was successful, false otherwise
     */
    public boolean clickStartAttemptButton(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        String threadName = Thread.currentThread().getName();
        
        try {
            log.info("{}: Clicking 'Start attempt' button for account: {}", threadName, username);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            String currentUrl = driver.getCurrentUrl();
            if (isAttemptPage(currentUrl)) {
                session.updateUrl(currentUrl);
                return true;
            }

            WebElement startButton = wait.until(
                    ExpectedConditions.elementToBeClickable(START_ATTEMPT_BUTTON_SELECTOR));
            startButton.click();

            wait.until(ExpectedConditions.urlContains("/mod/quiz/attempt.php"));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));

            currentUrl = driver.getCurrentUrl();
            session.updateUrl(currentUrl);

            boolean started = isAttemptPage(currentUrl);
            log.info("{}: Quiz attempt started for account: {}", threadName, username);
            return started;
        } catch (Exception e) {
            log.error("{}: Error clicking 'Start attempt' button for account {}: {}", 
                    threadName, username, e.getMessage());
            return false;
        }
    }
    
    /**
     * Click the "Finish attempt..." link on the test page
     *
     * @param session The navigation session
     * @return true if link click was successful, false otherwise
     */
    public boolean clickFinishAttemptLink(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        String threadName = Thread.currentThread().getName();
        
        try {
            log.info("{}: Navigating to summary page for account: {}", threadName, username);
            
            // Check if we have an active attempt with ID
            if (!session.hasActiveAttempt()) {
                log.error("{}: Cannot navigate to summary page - no active attempt ID found for account: {}", 
                        threadName, username);
                return false;
            }
            
            // Use direct navigation to summary.php instead of searching for a link
            String summaryUrl = session.getQuizTarget().summaryUrl(session.getAttemptId());
            log.info("{}: Navigating directly to summary page: {}", threadName, summaryUrl);
            driver.get(summaryUrl);
            
            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
            
            // Update session URL
            session.updateUrl(driver.getCurrentUrl());
            
            log.info("{}: Successfully navigated to summary page for account: {}", threadName, username);
            return true;
            
        } catch (Exception e) {
            log.error("{}: Error navigating to summary page for account {}: {}", 
                    threadName, username, e.getMessage());
            return false;
        }
    }
    
    /**
     * Click the "Submit all and finish" button on the summary page
     *
     * @param session The navigation session
     * @return true if button click was successful, false otherwise
     */
    public boolean clickSubmitAllButton(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        String threadName = Thread.currentThread().getName();
        
        try {
            log.info("{}: Clicking 'Submit all and finish' button for account: {}", threadName, username);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            // Wait for the button to be clickable
            WebElement submitButton = wait.until(
                    ExpectedConditions.elementToBeClickable(SUBMIT_ALL_BUTTON_SELECTOR));
            
            // Click the button
            submitButton.click();
            
            // Wait for confirmation modal to appear
            wait.until(ExpectedConditions.visibilityOfElementLocated(CONFIRM_SUBMIT_BUTTON_SELECTOR));
            
            // Update session URL
            session.updateUrl(driver.getCurrentUrl());
            
            log.info("{}: Successfully clicked 'Submit all and finish' button for account: {}", threadName, username);
            return true;
        } catch (Exception e) {
            log.error("{}: Error clicking 'Submit all and finish' button for account {}: {}", 
                    threadName, username, e.getMessage());
            return false;
        }
    }
    
    /**
     * Click the confirmation "Submit all and finish" button in the modal dialog
     *
     * @param session The navigation session
     * @return true if button click was successful, false otherwise
     */
    public boolean clickConfirmSubmitButton(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        String threadName = Thread.currentThread().getName();
        
        try {
            log.info("{}: Clicking confirmation 'Submit all and finish' button for account: {}", threadName, username);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            // Wait for the button to be clickable
            WebElement confirmButton = wait.until(
                    ExpectedConditions.elementToBeClickable(CONFIRM_SUBMIT_BUTTON_SELECTOR));
            
            // Click the button
            confirmButton.click();
            
            // Wait for final results page to load
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
            
            // Update session URL
            session.updateUrl(driver.getCurrentUrl());
            
            log.info("{}: Successfully clicked confirmation 'Submit all and finish' button for account: {}", threadName, username);
            return true;
        } catch (Exception e) {
            log.error("{}: Error clicking confirmation 'Submit all and finish' button for account {}: {}", 
                    threadName, username, e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for the authentication result (success or failure)
     *
     * @param driver browser used for authentication
     * @return detailed authentication result
     */
    private AuthenticationResult waitForAuthenticationResult(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        
        try {
            // Wait for either the user menu (success) or login errors (failure)
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(USER_MENU_SELECTOR),
                    ExpectedConditions.visibilityOfElementLocated(LOGIN_ERROR_SELECTOR)
            ));
            
            if (!driver.findElements(USER_MENU_SELECTOR).isEmpty()) {
                return new AuthenticationResult(
                        AuthenticationStatus.SUCCESS, "Authentication successful");
            }

            String message = driver.findElements(LOGIN_ERROR_SELECTOR).stream()
                    .findFirst()
                    .map(WebElement::getText)
                    .filter(text -> !text.isBlank())
                    .orElse("Invalid username or password");
            return new AuthenticationResult(AuthenticationStatus.INVALID_CREDENTIALS, message);
        } catch (Exception e) {
            log.warn("Timeout waiting for authentication result: {}", e.getMessage());
            String currentUrl = driver.getCurrentUrl();
            if (currentUrl != null && !currentUrl.contains("/login/")) {
                return new AuthenticationResult(
                        AuthenticationStatus.SUCCESS, "Authentication successful");
            }
            return new AuthenticationResult(
                    AuthenticationStatus.ERROR,
                    "TestCenter did not return an authentication result in time");
        }
    }

    static boolean isAttemptPage(String url) {
        return url != null
                && url.contains("/mod/quiz/attempt.php")
                && QuizTarget.extractAttemptId(url).isPresent();
    }
}
