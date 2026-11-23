package com.myprojects.kpok2.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for loading TestCenter settings from application.properties
 * Supports multiple accounts for parallel processing
 */
@ConfigurationProperties(prefix = "testcenter")
@Data
public class TestCenterProperties {

    /**
     * For backward compatibility - single credentials
     */
    private String username;
    @ToString.Exclude
    private String password;
    
    /**
     * Settings for parallel execution
     */
    private List<AccountProperties> accounts = new ArrayList<>();
    private NavigationProperties navigation = new NavigationProperties();
    
    /**
     * Class for storing data of a single account
     */
    @Data
    public static class AccountProperties {
        private String username;
        @ToString.Exclude
        private String password;
        private boolean enabled = true;
    }
    
    /**
     * Navigation settings
     */
    @Data
    public static class NavigationProperties {
        // Moodle quiz view URL selected by the user
        private String quizUrl = "";

        // Maximum number of parallel threads
        private int maxThreads = 1;
        
        // Thread timeout in seconds (browser stays open after navigation)
        private int threadTimeoutSeconds = 2;

        // Maximum time a worker may wait for a busy account to be released
        private int accountAcquireTimeoutSeconds = 10;

        // Stop a broken worker instead of retrying the same failure forever
        private int maxConsecutiveFailures = 3;

        // Pause between recoverable failures to avoid a tight retry loop
        private int failureRetryDelaySeconds = 5;
        
        // Whether to reuse browser sessions between iterations
        private boolean reuseSession = false;
    }
    
    /**
     * Get a list of active accounts
     */
    public List<AccountProperties> getEnabledAccounts() {
        List<AccountProperties> enabledAccounts = new ArrayList<>();
        
        for (AccountProperties account : accounts) {
            if (account.isEnabled()) {
                enabledAccounts.add(account);
            }
        }
        
        // If there are no active accounts in the list but global credentials exist
        if (enabledAccounts.isEmpty() && username != null && !username.isEmpty()) {
            AccountProperties defaultAccount = new AccountProperties();
            defaultAccount.setUsername(username);
            defaultAccount.setPassword(password);
            enabledAccounts.add(defaultAccount);
        }
        
        return enabledAccounts;
    }
}
