package com.myprojects.kpok2.service.navigation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a pool of TestCenter accounts for parallel processing.
 * Each thread will be assigned a unique account.
 */
@Slf4j
@Component
public class AccountManager {
    
    private final List<AccountCredentials> accountPool = new ArrayList<>();
    private final Lock accountLock = new ReentrantLock();
    
    /**
     * Acquire an available account for the current thread.
     * If no account is available, this method will block until one becomes available.
     *
     * @return AccountCredentials for the assigned account
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public AccountCredentials acquireAccount() throws InterruptedException {
        accountLock.lock();
        try {
            Optional<AccountCredentials> availableAccount;
            while (true) {
                availableAccount = accountPool.stream()
                        .filter(account -> !account.isInUse())
                        .findFirst();
                
                if (availableAccount.isPresent()) {
                    AccountCredentials account = availableAccount.get();
                    account.setInUse(true);
                    log.info("Account acquired: {}", account.getUsername());
                    return account;
                }
                
                // If no account is available, wait and try again
                log.warn("No accounts available, waiting for release...");
                // Release lock while waiting to avoid deadlock
                accountLock.unlock();
                Thread.sleep(1000);
                accountLock.lock();
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
                    .filter(a -> a.getUsername().equals(account.getUsername()))
                    .findFirst()
                    .ifPresent(a -> {
                        a.setInUse(false);
                        log.info("Account released: {}", a.getUsername());
                    });
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
            accountPool.add(new AccountCredentials(username, password));
            log.info("Added account to pool: {}", username);
        } finally {
            accountLock.unlock();
        }
    }
    
    /**
     * Get the current number of accounts in the pool.
     *
     * @return The number of accounts
     */
    public int getAccountCount() {
        return accountPool.size();
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
} 