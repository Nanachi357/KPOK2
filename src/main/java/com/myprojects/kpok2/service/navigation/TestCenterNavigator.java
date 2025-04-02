package com.myprojects.kpok2.service.navigation;

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
    private static final String TEST_URL = "https://test.testcentr.org.ua/mod/quiz/view.php?id=109";
    
    private static final By USERNAME_SELECTOR = By.cssSelector("input#username");
    private static final By PASSWORD_SELECTOR = By.cssSelector("input#password");
    private static final By LOGIN_BUTTON_SELECTOR = By.cssSelector("button#loginbtn");
    private static final By USER_MENU_SELECTOR = By.cssSelector("li.nav-item.dropdown.ml-3.usermenu");
    private static final By LOGIN_ERROR_SELECTOR = By.cssSelector(".loginerrors");
    
    // Selectors for test attempt buttons - Ukrainian text values match the TestCenter UI elements
    private static final By ATTEMPT_TEST_BUTTON_SELECTOR = By.cssSelector(".singlebutton .btn-primary");
    private static final By START_ATTEMPT_BUTTON_SELECTOR = By.cssSelector("#id_submitbutton");
    // Ukrainian text in selectors is preserved as it matches the actual text in the TestCenter UI
    private static final By START_ATTEMPT_TEXT_SELECTOR = By.xpath("//button[contains(text(), 'Почати спробу')]");
    private static final By START_ATTEMPT_ALT_SELECTOR = By.cssSelector("input[type='submit'].btn-primary, button[type='submit'].btn-primary");
    private static final By PREFLIGHT_FORM_SELECTOR = By.cssSelector("#mod_quiz_preflight_form");
    private static final By FINISH_ATTEMPT_LINK_SELECTOR = By.cssSelector(".endtestlink.aalink");
    // Ukrainian text in selectors is preserved as it matches the actual text in the TestCenter UI
    private static final By FINISH_ATTEMPT_XPATH_SELECTOR = By.xpath("//a[contains(text(), 'Завершити спробу')]");
    private static final By SUBMIT_ALL_BUTTON_SELECTOR = By.cssSelector(".submitbtns .btn-primary");
    private static final By CONFIRM_SUBMIT_BUTTON_SELECTOR = By.cssSelector("button.btn-primary[data-action='save']");
    
    // Updating the selector for the blocks drawer button with more precise class and attribute matching
    private static final By BLOCKS_DRAWER_TOGGLE_BUTTON = By.cssSelector("button.btn.icon-no-margin[data-toggler='drawers'][data-target='theme_boost-drawers-blocks']");
    
    // Changing timeout from 5 to 3 seconds
    private static final int DEFAULT_TIMEOUT_SECONDS = 3;

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

    /**
     * Authenticate to the TestCenter website
     *
     * @param session The navigation session containing account credentials and WebDriver
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        String username = session.getAccount().getUsername();
        String password = session.getAccount().getPassword();
        
        try {
            log.info("Navigating to login page for account: {}", username);
            driver.get(LOGIN_URL);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
            
            // Wait for login form to load
            wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_SELECTOR));
            
            // Enter credentials
            log.info("Entering credentials for account: {}", username);
            log.info("DEBUG - Password being used for account {}: {}", username, password); // Logging password for debugging
            
            // Clear fields before entering text
            WebElement usernameField = driver.findElement(USERNAME_SELECTOR);
            WebElement passwordField = driver.findElement(PASSWORD_SELECTOR);
            usernameField.clear();
            passwordField.clear();
            
            // Enter username
            usernameField.sendKeys(username);
            
            // Enter password slowly, character by character
            for (char c : password.toCharArray()) {
                passwordField.sendKeys(String.valueOf(c));
                try {
                    Thread.sleep(100); // Small delay between characters
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Shorter delay before clicking login button
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Click login button
            driver.findElement(LOGIN_BUTTON_SELECTOR).click();
            
            // Wait for either success or failure
            boolean success = waitForAuthenticationResult(session);
            
            if (success) {
                log.info("Authentication successful for account: {}", username);
                session.updateUrl(driver.getCurrentUrl());
                return true;
            } else {
                log.error("Authentication failed for account: {}", username);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during authentication for account {}: {}", username, e.getMessage());
            return false;
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
            driver.get(TEST_URL);
            
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
            
            // Check the button text to determine if it's a new attempt or resume attempt
            String buttonText = attemptButton.getText().trim();
            // Using more precise text checks to identify the "Continue attempt" button
            // Ukrainian texts are preserved as they match the actual texts in TestCenter UI
            boolean isResumeAttempt = buttonText.contains("Продовжуйте свою спробу") || 
                                      buttonText.contains("Продовжити") ||
                                      buttonText.contains("Продовжуйте");
            
            log.info("{}: Found button with text '{}' for account: {}. Is resume attempt: {}", 
                    threadName, buttonText, username, isResumeAttempt);
            
            // Click the button
            attemptButton.click();
            
            // Short delay after clicking
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Wait for page to load after button click - don't rely on preflight form
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
            
            // Update session URL
            session.updateUrl(driver.getCurrentUrl());
            
            log.info("{}: Successfully clicked '{}' button for account: {}", 
                    threadName, buttonText, username);
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
            
            // First try to find the button using different selectors
            WebElement startButton = null;
            
            // Attempt 1: by ID (original method)
            try {
                startButton = wait.until(
                        ExpectedConditions.elementToBeClickable(START_ATTEMPT_BUTTON_SELECTOR));
            } catch (Exception idEx) {
                log.warn("{}: Could not find 'Start attempt' button by ID, trying text: {}", threadName, idEx.getMessage());
                
                // Attempt 2: search by button text
                try {
                    startButton = wait.until(
                            ExpectedConditions.elementToBeClickable(START_ATTEMPT_TEXT_SELECTOR));
                } catch (Exception textEx) {
                    log.warn("{}: Could not find 'Start attempt' button by text, trying other selectors: {}", threadName, textEx.getMessage());
                    
                    // Attempt 3: search by type and class
                    try {
                        startButton = wait.until(
                                ExpectedConditions.elementToBeClickable(START_ATTEMPT_ALT_SELECTOR));
                    } catch (Exception altEx) {
                        log.warn("{}: Could not find 'Start attempt' button by alternative selectors: {}", threadName, altEx.getMessage());
                        
                        // Last chance attempt using JavaScript
                        try {
                            log.info("{}: Trying to find 'Start attempt' button using JavaScript", threadName);
                            String jsScript = 
                                    "var btns = document.querySelectorAll('button, input[type=\"submit\"]');" +
                                    "for(var i=0; i<btns.length; i++) {" +
                                    "  if(btns[i].textContent.includes('Почати спробу') || " +  // Ukrainian text matches the actual UI button text
                                    "     btns[i].value && btns[i].value.includes('Почати спробу')) {" +  // Ukrainian text matches the actual UI button value
                                    "    return btns[i];" +
                                    "  }" +
                                    "}" +
                                    "return null;";
                            
                            startButton = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsScript);
                        } catch (Exception jsEx) {
                            log.warn("{}: JavaScript approach also failed: {}", threadName, jsEx.getMessage());
                        }
                    }
                }
            }
            
            if (startButton != null) {
                // Click the found button
                startButton.click();
                
                // Wait for page to load (quiz page should load)
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
                
                // Update session URL
                session.updateUrl(driver.getCurrentUrl());
                
                log.info("{}: Successfully clicked 'Start attempt' button for account: {}", threadName, username);
                return true;
            } else {
                // If button not found by any method, check if we've already navigated to the test page
                // This can happen if the button was already clicked or the process started automatically
                
                // Check URL for "attempt.php"
                String currentUrl = driver.getCurrentUrl();
                if (currentUrl.contains("attempt.php")) {
                    log.info("{}: Already on attempt page without clicking button, URL: {}", threadName, currentUrl);
                    session.updateUrl(currentUrl);
                    return true;
                }
                
                // If that didn't help, try a direct JavaScript click on any confirmation button
                try {
                    String jsClickScript = 
                            "var buttons = document.querySelectorAll('.btn-primary, button[type=\"submit\"], input[type=\"submit\"]');" +
                            "for(var i=0; i<buttons.length; i++) {" +
                            "  if(buttons[i].offsetWidth > 0 && buttons[i].offsetHeight > 0) {" +
                            "    buttons[i].click();" +
                            "    return true;" +
                            "  }" +
                            "}" +
                            "return false;";
                    
                    boolean clicked = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsClickScript);
                    if (clicked) {
                        // Wait for page to load after click
                        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));
                        
                        // Update session URL
                        session.updateUrl(driver.getCurrentUrl());
                        
                        log.info("{}: Successfully clicked a submit button using JavaScript for account: {}", threadName, username);
                        return true;
                    }
                } catch (Exception jsEx) {
                    log.warn("{}: Failed to click any submit button with JavaScript: {}", threadName, jsEx.getMessage());
                }
                
                // Check if we have an attempt ID in URL despite the click error
                boolean hasAttemptId = currentUrl.contains("attempt.php") && currentUrl.contains("attempt=");
                if (hasAttemptId) {
                    log.info("{}: Despite button click error, found attempt ID in URL: {}", threadName, currentUrl);
                    session.updateUrl(currentUrl);
                    return true;
                }
                
                throw new Exception("Could not find or click 'Start attempt' button with any method");
            }
        } catch (Exception e) {
            log.error("{}: Error clicking 'Start attempt' button for account {}: {}", 
                    threadName, username, e.getMessage());
            
            // Check if we have an attempt ID in URL despite the click error
            String currentUrl = driver.getCurrentUrl();
            boolean hasAttemptId = currentUrl.contains("attempt.php") && currentUrl.contains("attempt=");
            if (hasAttemptId) {
                log.info("{}: Despite button click error, found attempt ID in URL: {}", threadName, currentUrl);
                session.updateUrl(currentUrl);
                return true;
            }
            
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
            String summaryUrl = "https://test.testcentr.org.ua/mod/quiz/summary.php?attempt=" + session.getAttemptId() + "&cmid=109";
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
     * @param session The navigation session
     * @return true if authentication was successful, false otherwise
     */
    private boolean waitForAuthenticationResult(NavigationSession session) {
        WebDriver driver = session.getWebDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        
        try {
            // Wait for either the user menu (success) or login errors (failure)
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(USER_MENU_SELECTOR),
                    ExpectedConditions.visibilityOfElementLocated(LOGIN_ERROR_SELECTOR)
            ));
            
            // Check if login was successful
            return driver.findElements(USER_MENU_SELECTOR).size() > 0;
        } catch (Exception e) {
            log.warn("Timeout waiting for authentication result: {}", e.getMessage());
            // Check current URL to determine if login was successful
            String currentUrl = driver.getCurrentUrl();
            return !currentUrl.contains("login") || currentUrl.contains("redirect");
        }
    }
} 