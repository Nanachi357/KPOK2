package com.myprojects.kpok2.service.catalog;

import com.myprojects.kpok2.model.QuizCatalogEntry;
import com.myprojects.kpok2.model.QuizCatalogSnapshot;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import com.myprojects.kpok2.service.navigation.AccountCredentials;
import com.myprojects.kpok2.service.navigation.TestCenterNavigator;
import com.myprojects.kpok2.service.navigation.TestCenterNavigator.AuthenticationResult;
import com.myprojects.kpok2.service.navigation.WebDriverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Validates TestCenter accounts and refreshes the authenticated quiz catalog.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizCatalogService {

    public static final String CATALOG_URL = "https://test.testcentr.org.ua/course/view.php?id=4";
    private static final String COURSE_PAGE_PATH = "/course/view.php";
    private static final String ENROLLMENT_PAGE_PATH = "/enrol/index.php";
    private static final By PAGE_CONTENT = By.cssSelector("#page-content");
    private static final By SELF_ENROLL_BUTTON = By.cssSelector(
            "form[action*='/enrol/index.php'] #id_submitbutton, #id_submitbutton");
    private static final By ENROLLMENT_KEY_INPUT = By.cssSelector(
            "input[name='enrolpassword'], input#id_enrolpassword");

    private final WebDriverFactory webDriverFactory;
    private final TestCenterNavigator navigator;
    private final QuizCatalogParser parser;
    private final QuizCatalogCache cache;

    public AuthenticationResult validateAccount(AccountDTO account) {
        WebDriver driver = webDriverFactory.createHeadlessDriver();
        try {
            return navigator.authenticate(driver, credentials(account));
        } finally {
            webDriverFactory.closeDriver(driver);
        }
    }

    public QuizCatalogSnapshot refreshCatalog(AccountDTO account) {
        WebDriver driver = webDriverFactory.createHeadlessDriver();
        try {
            AuthenticationResult authentication = navigator.authenticate(
                    driver, credentials(account));
            if (!authentication.isSuccess()) {
                throw new IllegalStateException(authentication.message());
            }

            driver.get(CATALOG_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(PAGE_CONTENT));
            ensureAuthenticatedCatalogPage(driver, wait);

            List<QuizCatalogEntry> entries = parser.parse(
                    Jsoup.parse(driver.getPageSource(), CATALOG_URL));
            if (entries.isEmpty()) {
                throw new IllegalStateException(
                        "No quizzes were found in the TestCenter catalog for this account");
            }

            QuizCatalogSnapshot snapshot = cache.save(entries);
            log.info("Refreshed TestCenter catalog with {} quizzes", entries.size());
            return snapshot;
        } finally {
            webDriverFactory.closeDriver(driver);
        }
    }

    public QuizCatalogSnapshot loadCachedCatalog() {
        return cache.load();
    }

    private AccountCredentials credentials(AccountDTO account) {
        return new AccountCredentials(account.getUsername(), account.getPassword());
    }

    private void ensureAuthenticatedCatalogPage(WebDriver driver, WebDriverWait wait) {
        String currentUrl = driver.getCurrentUrl();
        if (currentUrl.contains("/login/")) {
            throw new IllegalStateException(
                    "The TestCenter session expired while loading the catalog");
        }

        if (!currentUrl.contains(ENROLLMENT_PAGE_PATH)) {
            return;
        }

        if (!driver.findElements(ENROLLMENT_KEY_INPUT).isEmpty()) {
            throw new IllegalStateException(
                    "The KROK course requires an enrollment key for this account");
        }

        log.info("Account is not enrolled in the KROK course; enrolling automatically");
        WebElement enrollButton = wait.until(
                ExpectedConditions.elementToBeClickable(SELF_ENROLL_BUTTON));
        enrollButton.click();

        wait.until(ExpectedConditions.urlContains(COURSE_PAGE_PATH));
        wait.until(ExpectedConditions.visibilityOfElementLocated(PAGE_CONTENT));

        String enrolledUrl = driver.getCurrentUrl();
        if (!enrolledUrl.contains(COURSE_PAGE_PATH)) {
            throw new IllegalStateException(
                    "TestCenter did not open the KROK catalog after enrollment");
        }
        log.info("Account enrolled in the KROK course successfully");
    }
}
