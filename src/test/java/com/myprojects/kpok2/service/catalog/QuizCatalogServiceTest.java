package com.myprojects.kpok2.service.catalog;

import com.myprojects.kpok2.model.QuizCatalogEntry;
import com.myprojects.kpok2.model.QuizCatalogSnapshot;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import com.myprojects.kpok2.service.navigation.AccountCredentials;
import com.myprojects.kpok2.service.navigation.TestCenterNavigator;
import com.myprojects.kpok2.service.navigation.TestCenterNavigator.AuthenticationResult;
import com.myprojects.kpok2.service.navigation.TestCenterNavigator.AuthenticationStatus;
import com.myprojects.kpok2.service.navigation.WebDriverFactory;
import org.junit.jupiter.api.Test;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizCatalogServiceTest {

    @Test
    void validatesAccountAndAlwaysClosesItsBrowser() {
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        TestCenterNavigator navigator = mock(TestCenterNavigator.class);
        WebDriver driver = mock(WebDriver.class);
        when(webDriverFactory.createHeadlessDriver()).thenReturn(driver);
        AuthenticationResult expected = new AuthenticationResult(
                AuthenticationStatus.SUCCESS, "Authentication successful");
        when(navigator.authenticate(any(WebDriver.class), any(AccountCredentials.class)))
                .thenReturn(expected);

        QuizCatalogService service = new QuizCatalogService(
                webDriverFactory,
                navigator,
                mock(QuizCatalogParser.class),
                mock(QuizCatalogCache.class));

        AuthenticationResult actual = service.validateAccount(
                new AccountDTO("student", "secret", true));

        assertEquals(expected, actual);
        verify(webDriverFactory).closeDriver(driver);
    }

    @Test
    void stopsCatalogRefreshWhenAuthenticationFailsAndClosesBrowser() {
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        TestCenterNavigator navigator = mock(TestCenterNavigator.class);
        WebDriver driver = mock(WebDriver.class);
        when(webDriverFactory.createHeadlessDriver()).thenReturn(driver);
        when(navigator.authenticate(any(WebDriver.class), any(AccountCredentials.class)))
                .thenReturn(new AuthenticationResult(
                        AuthenticationStatus.INVALID_CREDENTIALS,
                        "Invalid username or password"));

        QuizCatalogService service = new QuizCatalogService(
                webDriverFactory,
                navigator,
                mock(QuizCatalogParser.class),
                mock(QuizCatalogCache.class));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.refreshCatalog(new AccountDTO("student", "wrong", true)));

        assertEquals("Invalid username or password", error.getMessage());
        verify(webDriverFactory).closeDriver(driver);
    }

    @Test
    void selfEnrollsBeforeRefreshingCatalogWhenNoKeyIsRequired() {
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        TestCenterNavigator navigator = mock(TestCenterNavigator.class);
        QuizCatalogParser parser = mock(QuizCatalogParser.class);
        QuizCatalogCache cache = mock(QuizCatalogCache.class);
        WebDriver driver = mock(WebDriver.class);
        WebElement pageContent = mock(WebElement.class);
        WebElement enrollButton = mock(WebElement.class);

        when(webDriverFactory.createHeadlessDriver()).thenReturn(driver);
        when(navigator.authenticate(any(WebDriver.class), any(AccountCredentials.class)))
                .thenReturn(new AuthenticationResult(
                        AuthenticationStatus.SUCCESS, "Authentication successful"));
        when(driver.findElement(By.cssSelector("#page-content"))).thenReturn(pageContent);
        when(pageContent.isDisplayed()).thenReturn(true);
        when(driver.findElement(By.cssSelector(
                "form[action*='/enrol/index.php'] #id_submitbutton, #id_submitbutton")))
                .thenReturn(enrollButton);
        when(enrollButton.isDisplayed()).thenReturn(true);
        when(enrollButton.isEnabled()).thenReturn(true);
        when(driver.getCurrentUrl()).thenReturn(
                "https://test.testcentr.org.ua/enrol/index.php?id=4",
                QuizCatalogService.CATALOG_URL,
                QuizCatalogService.CATALOG_URL);
        when(driver.getPageSource()).thenReturn("<html><body></body></html>");

        QuizCatalogEntry entry = new QuizCatalogEntry(
                173L,
                "Крок 3 Психіатрія (UA)",
                "КРОК 3",
                "KROK 3",
                "Психіатрія",
                "uk",
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");
        QuizCatalogSnapshot snapshot = new QuizCatalogSnapshot(
                "2026-11-12T17:00:00Z", List.of(entry));
        when(parser.parse(any(Document.class))).thenReturn(List.of(entry));
        when(cache.save(List.of(entry))).thenReturn(snapshot);

        QuizCatalogService service = new QuizCatalogService(
                webDriverFactory, navigator, parser, cache);

        QuizCatalogSnapshot actual = service.refreshCatalog(
                new AccountDTO("student", "secret", true));

        assertEquals(snapshot, actual);
        verify(enrollButton).click();
        verify(webDriverFactory).closeDriver(driver);
    }

    @Test
    void refusesAutomaticEnrollmentWhenCourseRequiresAKey() {
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        TestCenterNavigator navigator = mock(TestCenterNavigator.class);
        WebDriver driver = mock(WebDriver.class);
        WebElement pageContent = mock(WebElement.class);
        WebElement enrollmentKey = mock(WebElement.class);

        when(webDriverFactory.createHeadlessDriver()).thenReturn(driver);
        when(navigator.authenticate(any(WebDriver.class), any(AccountCredentials.class)))
                .thenReturn(new AuthenticationResult(
                        AuthenticationStatus.SUCCESS, "Authentication successful"));
        when(driver.findElement(By.cssSelector("#page-content"))).thenReturn(pageContent);
        when(pageContent.isDisplayed()).thenReturn(true);
        when(driver.getCurrentUrl()).thenReturn(
                "https://test.testcentr.org.ua/enrol/index.php?id=4");
        when(driver.findElements(By.cssSelector(
                "input[name='enrolpassword'], input#id_enrolpassword")))
                .thenReturn(List.of(enrollmentKey));

        QuizCatalogService service = new QuizCatalogService(
                webDriverFactory,
                navigator,
                mock(QuizCatalogParser.class),
                mock(QuizCatalogCache.class));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.refreshCatalog(
                        new AccountDTO("student", "secret", true)));

        assertEquals(
                "The KROK course requires an enrollment key for this account",
                error.getMessage());
        verify(driver, never()).getPageSource();
        verify(webDriverFactory).closeDriver(driver);
    }
}
