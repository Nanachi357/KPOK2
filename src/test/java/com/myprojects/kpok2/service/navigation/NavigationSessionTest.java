package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.model.QuizTarget;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NavigationSessionTest {

    @Test
    void derivesReviewUrlFromTheSelectedQuizAndCapturedAttempt() {
        QuizTarget target = QuizTarget.fromUrl(
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");
        NavigationSession session = new NavigationSession(
                mock(WebDriverFactory.class),
                mock(AccountManager.class),
                mock(WebDriver.class),
                new AccountCredentials("student", "secret"),
                target);

        session.updateUrl(
                "https://test.testcentr.org.ua/mod/quiz/attempt.php?attempt=2593848&cmid=173&page=0");

        assertEquals("2593848", session.getAttemptId());
        assertEquals(List.of(target.reviewUrl("2593848")), session.getResultPageUrls());
    }

    @Test
    void closesBrowserBeforeReleasingAccountAndIsIdempotent() {
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        AccountManager accountManager = mock(AccountManager.class);
        WebDriver driver = mock(WebDriver.class);
        AccountCredentials account = new AccountCredentials("student", "secret");
        NavigationSession session = new NavigationSession(
                webDriverFactory,
                accountManager,
                driver,
                account,
                QuizTarget.fromUrl(
                        "https://test.testcentr.org.ua/mod/quiz/view.php?id=173"));

        session.close();
        session.close();

        var ordered = inOrder(webDriverFactory, accountManager);
        ordered.verify(webDriverFactory).closeDriver();
        ordered.verify(accountManager).releaseAccount(account);
        verify(webDriverFactory, times(1)).closeDriver();
    }
}
