package com.myprojects.kpok2.service;

import com.myprojects.kpok2.security.PasswordEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountConfigurationServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsDuplicateUsernamesCaseInsensitively() {
        AccountConfigurationService service = service();
        service.addAccount(new AccountConfigurationService.AccountDTO(
                "Student", "first-password", true));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.addAccount(new AccountConfigurationService.AccountDTO(
                        " student ", "second-password", true)));
        assertEquals(1, service.getAccounts().size());
    }

    @Test
    void clampsNavigationSettingsToSupportedBounds() {
        AccountConfigurationService service = service();

        service.updateNavigationSettings(
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173",
                Integer.MAX_VALUE,
                -10,
                false);

        assertEquals(10, service.getMaxThreads());
        assertEquals(0, service.getIterationCount());
    }

    private AccountConfigurationService service() {
        PasswordEncryptor encryptor = mock(PasswordEncryptor.class);
        when(encryptor.encryptPassword(anyString())).thenReturn("encrypted-value");
        Environment environment = mock(Environment.class);
        when(environment.getProperty("testcenter.navigation.quiz-url", ""))
                .thenReturn("");
        return new AccountConfigurationService(
                encryptor,
                environment,
                temporaryDirectory + File.separator);
    }
}
