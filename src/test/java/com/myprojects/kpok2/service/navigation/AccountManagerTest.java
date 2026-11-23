package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountManagerTest {

    @Test
    void credentialStringRepresentationNeverContainsThePassword() {
        AccountCredentials credentials = new AccountCredentials(
                "student", "unique-secret-value");

        assertFalse(credentials.toString().contains("unique-secret-value"));
        assertNotEquals("", credentials.toString());

        TestCenterProperties properties = new TestCenterProperties();
        properties.setPassword("legacy-unique-secret");
        TestCenterProperties.AccountProperties account =
                new TestCenterProperties.AccountProperties();
        account.setUsername("student");
        account.setPassword("list-unique-secret");
        properties.setAccounts(List.of(account));
        assertFalse(properties.toString().contains("legacy-unique-secret"));
        assertFalse(properties.toString().contains("list-unique-secret"));
    }

    @Test
    void failsImmediatelyWhenNoEnabledAccountsExist() {
        AccountManager manager = new AccountManager();

        assertTimeout(Duration.ofSeconds(1), () ->
                assertThrows(
                        IllegalStateException.class,
                        () -> manager.acquireAccount(Duration.ofSeconds(10))));
    }

    @Test
    void waitsForReleaseWithoutPollingAndThenReusesTheAccount() throws Exception {
        AccountManager manager = new AccountManager();
        assertTrue(manager.replaceAccounts(List.of(
                new AccountCredentials("student", "password"))));
        AccountCredentials firstLease = manager.acquireAccount(Duration.ofSeconds(1));
        var executor = Executors.newSingleThreadExecutor();

        try {
            var waitingLease = executor.submit(() ->
                    manager.acquireAccount(Duration.ofSeconds(2)));
            Thread.sleep(50);
            assertFalse(waitingLease.isDone());

            manager.releaseAccount(firstLease);

            assertSame(firstLease, waitingLease.get(1, TimeUnit.SECONDS));
        } finally {
            manager.releaseAccount(firstLease);
            executor.shutdownNow();
        }
    }

    @Test
    void timesOutWhenEveryAccountRemainsBusy() throws Exception {
        AccountManager manager = new AccountManager();
        assertTrue(manager.replaceAccounts(List.of(
                new AccountCredentials("student", "password"))));
        AccountCredentials lease = manager.acquireAccount(Duration.ofSeconds(1));

        try {
            assertTimeout(Duration.ofSeconds(1), () ->
                    assertThrows(
                            IllegalStateException.class,
                            () -> manager.acquireAccount(Duration.ofMillis(50))));
        } finally {
            manager.releaseAccount(lease);
        }
    }

    @Test
    void defersPoolReplacementWhileAnAccountIsInUseAndDeduplicatesRefresh() throws Exception {
        AccountManager manager = new AccountManager();
        assertTrue(manager.replaceAccounts(List.of(
                new AccountCredentials("student", "old-password"))));
        AccountCredentials lease = manager.acquireAccount(Duration.ofSeconds(1));

        assertFalse(manager.replaceAccounts(List.of(
                new AccountCredentials("replacement", "new-password"))));

        manager.releaseAccount(lease);
        assertTrue(manager.replaceAccounts(List.of(
                new AccountCredentials("Replacement", "new-password"),
                new AccountCredentials("replacement", "duplicate-password"))));
        AccountCredentials replacement = manager.acquireAccount(Duration.ofSeconds(1));
        assertEquals(
                "Replacement",
                replacement.getUsername());
        manager.releaseAccount(replacement);
    }
}
