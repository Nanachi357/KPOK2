package com.myprojects.kpok2.service;

import com.myprojects.kpok2.config.TestCenterConfig;
import com.myprojects.kpok2.util.TestParserConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
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

    public boolean login() {
        try {
            log.info("Starting authentication process...");
            log.debug("Opening login page: {}", config.getLoginUrl());
            webDriver.get(config.getLoginUrl());

            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));

            log.debug("Current URL before login: {}", webDriver.getCurrentUrl());
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
            wait.until(ExpectedConditions.urlToBe("https://test.testcentr.org.ua/my/"));

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
}