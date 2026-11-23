package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavigationSessionFactoryTest {

    @Test
    void releasesAccountWhenBrowserCreationFails() throws Exception {
        AccountManager accountManager = mock(AccountManager.class);
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        TestCenterProperties properties = new TestCenterProperties();
        properties.getNavigation().setAccountAcquireTimeoutSeconds(7);
        AccountConfigurationService accountService = mock(AccountConfigurationService.class);
        AccountCredentials account = new AccountCredentials("student", "password");
        when(accountService.getQuizTarget()).thenReturn(QuizTarget.fromUrl(
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173"));
        when(accountManager.acquireAccount(any(Duration.class))).thenReturn(account);
        when(webDriverFactory.getDriver()).thenThrow(
                new IllegalStateException("browser unavailable"));
        NavigationSessionFactory factory = new NavigationSessionFactory(
                accountManager, webDriverFactory, properties, accountService);

        assertThrows(IllegalStateException.class, factory::createSession);

        verify(accountManager).acquireAccount(Duration.ofSeconds(7));
        verify(accountManager).releaseAccount(account);
    }

    @Test
    void disabledSavedAccountsDoNotFallBackToLegacyCredentials() {
        AccountManager accountManager = mock(AccountManager.class);
        when(accountManager.replaceAccounts(anyList())).thenReturn(true);
        WebDriverFactory webDriverFactory = mock(WebDriverFactory.class);
        TestCenterProperties properties = new TestCenterProperties();
        properties.setUsername("legacy");
        properties.setPassword("legacy-password");
        AccountConfigurationService accountService = mock(AccountConfigurationService.class);
        when(accountService.getAccounts()).thenReturn(FXCollections.observableArrayList(
                new AccountDTO("disabled", "password", false)));
        NavigationSessionFactory factory = new NavigationSessionFactory(
                accountManager, webDriverFactory, properties, accountService);

        assertTrue(factory.refreshAccountPool());

        verify(accountManager).replaceAccounts(List.of());
    }
}
