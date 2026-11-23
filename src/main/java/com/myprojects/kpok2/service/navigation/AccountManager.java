package com.myprojects.kpok2.service.navigation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a pool of TestCenter accounts for parallel processing.
 * Each thread will be assigned a unique account.
 */
@Slf4j
@Component
public class AccountManager {

    private final List<AccountCredentials> accountPool = new ArrayList<>();
    private final ReentrantLock accountLock = new ReentrantLock(true);
    private final Condition accountAvailable = accountLock.newCondition();

    /**
     * Acquire an available account for the current thread.
     * If every configured account is busy, wait only for the supplied timeout.
     *
     * @return AccountCredentials for the assigned account
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public AccountCredentials acquireAccount(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Account acquisition timeout cannot be negative");
        }

        long remainingNanos = timeout.toNanos();
        accountLock.lockInterruptibly();
        try {
            while (true) {
                if (accountPool.isEmpty()) {
                    throw new IllegalStateException(
                            "No enabled TestCenter accounts are configured");
                }

                AccountCredentials availableAccount = accountPool.stream()
                        .filter(account -> !account.isInUse())
                        .findFirst()
                        .orElse(null);
                
                if (availableAccount != null) {
                    availableAccount.setInUse(true);
                    log.info("Account acquired: {}", availableAccount.getUsername());
                    return availableAccount;
                }

                if (remainingNanos <= 0) {
                    throw new IllegalStateException(
                            "Timed out waiting for an available TestCenter account");
                }
                remainingNanos = accountAvailable.awaitNanos(remainingNanos);
            }
        } finally {
            accountLock.unlock();
        }
    }

    /**
     * Release an account back to the pool.
     *
     * @param account The account to release
     */
    public void releaseAccount(AccountCredentials account) {
        accountLock.lock();
        try {
            accountPool.stream()
                    .filter(candidate -> candidate == account)
                    .findFirst()
                    .ifPresentOrElse(candidate -> {
                        candidate.setInUse(false);
                        accountAvailable.signal();
                        log.info("Account released: {}", candidate.getUsername());
                    }, () -> log.warn("Ignoring release of an account outside the active pool"));
        } finally {
            accountLock.unlock();
        }
    }
    
    /**
     * Add a new account to the pool.
     *
     * @param username Account username
     * @param password Account password
     */
    public void addAccount(String username, String password) {
        accountLock.lock();
        try {
            String normalizedUsername = normalizeUsername(username);
            boolean duplicate = accountPool.stream().anyMatch(account ->
                    account.getUsername().equalsIgnoreCase(normalizedUsername));
            if (duplicate) {
                log.warn("Account already exists in pool: {}", normalizedUsername);
                return;
            }
            accountPool.add(new AccountCredentials(normalizedUsername, password));
            accountAvailable.signal();
            log.info("Added account to pool: {}", normalizedUsername);
        } finally {
            accountLock.unlock();
        }
    }

    /**
     * Atomically replaces the idle account pool. Active sessions keep their
     * current pool until they close, and the new configuration is applied on
     * the next refresh.
     *
     * @return true when the replacement was applied; false when active
     * sessions made replacement unsafe
     */
    public boolean replaceAccounts(List<AccountCredentials> accounts) {
        Objects.requireNonNull(accounts, "accounts");
        accountLock.lock();
        try {
            long inUseCount = accountPool.stream()
                    .filter(AccountCredentials::isInUse)
                    .count();
            if (inUseCount > 0) {
                log.warn(
                        "Deferring account-pool refresh while {} account(s) are in use",
                        inUseCount);
                return false;
            }

            Map<String, AccountCredentials> uniqueAccounts = new LinkedHashMap<>();
            for (AccountCredentials account : accounts) {
                if (account == null || account.getUsername() == null
                        || account.getUsername().isBlank()) {
                    continue;
                }
                String username = normalizeUsername(account.getUsername());
                uniqueAccounts.putIfAbsent(
                        username.toLowerCase(Locale.ROOT),
                        new AccountCredentials(username, account.getPassword()));
            }

            accountPool.clear();
            accountPool.addAll(uniqueAccounts.values());
            accountAvailable.signalAll();
            log.info("Replaced account pool with {} enabled account(s)", accountPool.size());
            return true;
        } finally {
            accountLock.unlock();
        }
    }
    
    /**
     * Clear all accounts from the pool.
     */
    public void clearAccounts() {
        if (!replaceAccounts(List.of())) {
            log.warn("Account pool was not cleared because sessions are active");
        }
    }

    /**
     * Get the current number of accounts in the pool.
     *
     * @return The number of accounts
     */
    public int getAccountCount() {
        accountLock.lock();
        try {
            return accountPool.size();
        } finally {
            accountLock.unlock();
        }
    }
    
    /**
     * Get the number of available (not in use) accounts.
     *
     * @return The number of available accounts
     */
    public int getAvailableAccountCount() {
        accountLock.lock();
        try {
            return (int) accountPool.stream()
                    .filter(account -> !account.isInUse())
                    .count();
        } finally {
            accountLock.unlock();
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Account username cannot be blank");
        }
        return username.trim();
    }
}
