package com.myprojects.kpok2.service;

import com.myprojects.kpok2.config.TestCenterConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthenticationService {
    private final WebDriver webDriver;
    private final TestCenterConfig config;

    public AuthenticationService(WebDriver webDriver, TestCenterConfig config) {
        this.webDriver = webDriver;
        this.config = config;
    }

    public boolean login() {
        try {
            webDriver.get(config.getLoginUrl());

            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));

            // Wait for login form to be present
            WebElement loginForm = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("login")));

            // Find input fields
            WebElement usernameInput = webDriver.findElement(By.id("username"));
            WebElement passwordInput = webDriver.findElement(By.id("password"));
            WebElement submitButton = webDriver.findElement(By.id("loginbtn"));

            // Input credentials
            usernameInput.sendKeys(config.getUsername());
            passwordInput.sendKeys(config.getPassword());

            // Submit login form
            submitButton.click();

            // Wait for successful login (check for element that exists only for authenticated users)
            // Note: We need to update this selector when we can see the authenticated page
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("login")));

            return true;
        } catch (Exception e) {
            System.err.println("Login failed: " + e.getMessage());
            return false;
        }
    }
}