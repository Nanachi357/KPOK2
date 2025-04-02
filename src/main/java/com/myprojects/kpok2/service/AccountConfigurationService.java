package com.myprojects.kpok2.service;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.config.TestCenterProperties.AccountProperties;
import com.myprojects.kpok2.security.PasswordEncryptor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Service for managing account configurations
 * Provides methods for loading, saving, adding, updating and removing accounts
 */
@Slf4j
@Service
public class AccountConfigurationService {

    private final PasswordEncryptor passwordEncryptor;
    private final Properties properties;
    private final Environment environment;
    private final String propertiesPath;
    private final ObservableList<AccountDTO> accounts = FXCollections.observableArrayList();
    private static final String ACCOUNT_PREFIX = "testcenter.accounts";
    private static final String USERNAME_SUFFIX = ".username";
    private static final String PASSWORD_SUFFIX = ".password";
    private static final String ENABLED_SUFFIX = ".enabled";
    private static final String ENCRYPTED_PREFIX = "ENC:";
    
    // Navigation configuration constants
    private static final String NAVIGATION_PREFIX = "testcenter.navigation";
    private static final String MAX_THREADS_SUFFIX = ".max-threads";
    private static final String THREAD_TIMEOUT_SUFFIX = ".thread-timeout-seconds";
    private static final String ITERATION_COUNT_SUFFIX = ".iteration-count";
    private static final String REUSE_SESSION_SUFFIX = ".reuse-session";
    private static final int DEFAULT_MAX_THREADS = 2;
    private static final int DEFAULT_THREAD_TIMEOUT = 2;
    private static final int DEFAULT_ITERATION_COUNT = 10; // Default 10 iterations
    private static final boolean DEFAULT_REUSE_SESSION = false;
    
    // Current navigation settings
    private int maxThreads = DEFAULT_MAX_THREADS;
    private int threadTimeout = DEFAULT_THREAD_TIMEOUT;
    private int iterationCount = DEFAULT_ITERATION_COUNT;
    private boolean reuseSession = DEFAULT_REUSE_SESSION;

    @Autowired
    public AccountConfigurationService(
            PasswordEncryptor passwordEncryptor,
            Environment environment,
            @Value("${spring.config.additional-location:}") String configLocation) {
        this.passwordEncryptor = passwordEncryptor;
        this.environment = environment;
        this.properties = new Properties();
        
        // Determine the properties file path
        String appConfigPath;
        if (configLocation != null && !configLocation.isEmpty()) {
            appConfigPath = configLocation.replace("file:", "");
        } else {
            appConfigPath = System.getProperty("user.home") + "/.kpok2/";
        }
        
        // Ensure path exists
        File configDir = new File(appConfigPath);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        this.propertiesPath = appConfigPath + "application-local.properties";
        
        // Load accounts
        loadAccounts();
    }

    /**
     * Load accounts from properties file
     */
    public void loadAccounts() {
        try {
            File propsFile = new File(propertiesPath);
            if (propsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(propsFile)) {
                    properties.load(fis);
                }
            } else {
                // If the file doesn't exist, try to load from the application context
                // and create the file
                loadFromEnvironment();
                saveProperties();
            }
            
            // Clear accounts and load from properties
            accounts.clear();
            
            // Find all account indices
            Set<Integer> indices = new HashSet<>();
            for (Object key : properties.keySet()) {
                String keyStr = (String) key;
                if (keyStr.startsWith(ACCOUNT_PREFIX) && keyStr.contains(USERNAME_SUFFIX)) {
                    try {
                        String indexStr = keyStr.substring(
                                (ACCOUNT_PREFIX + "[").length(),
                                keyStr.indexOf("]" + USERNAME_SUFFIX)
                        );
                        indices.add(Integer.parseInt(indexStr));
                    } catch (Exception e) {
                        log.warn("Invalid account property key format: {}", keyStr);
                    }
                }
            }
            
            // Load each account
            for (Integer index : indices) {
                String usernameKey = ACCOUNT_PREFIX + "[" + index + "]" + USERNAME_SUFFIX;
                String passwordKey = ACCOUNT_PREFIX + "[" + index + "]" + PASSWORD_SUFFIX;
                String enabledKey = ACCOUNT_PREFIX + "[" + index + "]" + ENABLED_SUFFIX;
                
                String username = properties.getProperty(usernameKey);
                String encPassword = properties.getProperty(passwordKey);
                String enabled = properties.getProperty(enabledKey, "false");
                
                if (username != null && encPassword != null) {
                    // Decrypt password if it's encrypted
                    String password = decryptPasswordIfNeeded(encPassword);
                    
                    AccountDTO account = new AccountDTO(
                            username,
                            password,
                            Boolean.parseBoolean(enabled)
                    );
                    accounts.add(account);
                }
            }
            
            // Load navigation settings
            loadNavigationSettings();
            
            log.info("Loaded {} accounts from configuration", accounts.size());
        } catch (Exception e) {
            log.error("Failed to load accounts", e);
        }
    }

    /**
     * Load navigation settings from properties
     */
    private void loadNavigationSettings() {
        String maxThreadsKey = NAVIGATION_PREFIX + MAX_THREADS_SUFFIX;
        String threadTimeoutKey = NAVIGATION_PREFIX + THREAD_TIMEOUT_SUFFIX;
        String iterationCountKey = NAVIGATION_PREFIX + ITERATION_COUNT_SUFFIX;
        String reuseSessionKey = NAVIGATION_PREFIX + REUSE_SESSION_SUFFIX;
        
        try {
            String maxThreadsStr = properties.getProperty(maxThreadsKey);
            if (maxThreadsStr != null && !maxThreadsStr.isEmpty()) {
                maxThreads = Integer.parseInt(maxThreadsStr);
            } else {
                maxThreads = DEFAULT_MAX_THREADS;
            }
            
            String threadTimeoutStr = properties.getProperty(threadTimeoutKey);
            if (threadTimeoutStr != null && !threadTimeoutStr.isEmpty()) {
                threadTimeout = Integer.parseInt(threadTimeoutStr);
            } else {
                threadTimeout = DEFAULT_THREAD_TIMEOUT;
            }
            
            String iterationCountStr = properties.getProperty(iterationCountKey);
            if (iterationCountStr != null && !iterationCountStr.isEmpty()) {
                iterationCount = Integer.parseInt(iterationCountStr);
            } else {
                iterationCount = DEFAULT_ITERATION_COUNT;
            }
            
            String reuseSessionStr = properties.getProperty(reuseSessionKey);
            if (reuseSessionStr != null && !reuseSessionStr.isEmpty()) {
                reuseSession = Boolean.parseBoolean(reuseSessionStr);
            } else {
                reuseSession = DEFAULT_REUSE_SESSION;
            }
            
            log.info("Loaded navigation settings: maxThreads={}, threadTimeout={}, iterationCount={}, reuseSession={}", 
                     maxThreads, threadTimeout, iterationCount, reuseSession);
        } catch (Exception e) {
            maxThreads = DEFAULT_MAX_THREADS;
            threadTimeout = DEFAULT_THREAD_TIMEOUT;
            iterationCount = DEFAULT_ITERATION_COUNT;
            reuseSession = DEFAULT_REUSE_SESSION;
            log.warn("Error parsing navigation settings, using defaults: maxThreads={}, threadTimeout={}, iterationCount={}, reuseSession={}", 
                     maxThreads, threadTimeout, iterationCount, reuseSession);
        }
    }
    
    /**
     * Load accounts from Spring environment
     */
    private void loadFromEnvironment() {
        try {
            // Try to get accounts from environment
            Map<String, String> accountProps = new HashMap<>();
            
            // Get the username, password, and enabled status from environment
            String username = environment.getProperty("testcenter.username");
            String password = environment.getProperty("testcenter.password");
            if (username != null && password != null) {
                properties.setProperty("testcenter.username", username);
                properties.setProperty("testcenter.password", password);
                
                // Add as the first account
                accountProps.put(ACCOUNT_PREFIX + "[0]" + USERNAME_SUFFIX, username);
                accountProps.put(ACCOUNT_PREFIX + "[0]" + PASSWORD_SUFFIX, encryptPassword(password));
                accountProps.put(ACCOUNT_PREFIX + "[0]" + ENABLED_SUFFIX, "true");
            }
            
            // Get accounts from TestCenterProperties
            TestCenterProperties testCenterProps = new TestCenterProperties();
            if (testCenterProps.getAccounts() != null) {
                int index = accountProps.isEmpty() ? 0 : 1;
                for (AccountProperties account : testCenterProps.getAccounts()) {
                    if (account.getUsername() != null && account.getPassword() != null) {
                        accountProps.put(ACCOUNT_PREFIX + "[" + index + "]" + USERNAME_SUFFIX, account.getUsername());
                        accountProps.put(ACCOUNT_PREFIX + "[" + index + "]" + PASSWORD_SUFFIX,
                                encryptPassword(account.getPassword()));
                        accountProps.put(ACCOUNT_PREFIX + "[" + index + "]" + ENABLED_SUFFIX,
                                String.valueOf(account.isEnabled()));
                        index++;
                    }
                }
            }
            
            // Add to properties
            properties.putAll(accountProps);
            
            // Add default navigation settings
            properties.setProperty(NAVIGATION_PREFIX + MAX_THREADS_SUFFIX, String.valueOf(DEFAULT_MAX_THREADS));
            properties.setProperty(NAVIGATION_PREFIX + THREAD_TIMEOUT_SUFFIX, String.valueOf(DEFAULT_THREAD_TIMEOUT));
            properties.setProperty(NAVIGATION_PREFIX + ITERATION_COUNT_SUFFIX, String.valueOf(DEFAULT_ITERATION_COUNT));
            properties.setProperty(NAVIGATION_PREFIX + REUSE_SESSION_SUFFIX, String.valueOf(DEFAULT_REUSE_SESSION));
            
        } catch (Exception e) {
            log.error("Failed to load accounts from environment", e);
        }
    }
    
    /**
     * Save properties to file
     */
    public void saveAccounts() {
        try {
            // Update properties with current accounts
            updatePropertiesFromAccounts();
            
            // Add navigation settings to properties
            properties.setProperty(NAVIGATION_PREFIX + MAX_THREADS_SUFFIX, String.valueOf(maxThreads));
            properties.setProperty(NAVIGATION_PREFIX + THREAD_TIMEOUT_SUFFIX, String.valueOf(DEFAULT_THREAD_TIMEOUT));
            properties.setProperty(NAVIGATION_PREFIX + ITERATION_COUNT_SUFFIX, String.valueOf(iterationCount));
            properties.setProperty(NAVIGATION_PREFIX + REUSE_SESSION_SUFFIX, String.valueOf(reuseSession));
            
            // Save properties
            saveProperties();
            
            log.info("Saved {} accounts to configuration with navigation settings: maxThreads={}, threadTimeout={}, iterationCount={}, reuseSession={}", 
                    accounts.size(), maxThreads, DEFAULT_THREAD_TIMEOUT, iterationCount, reuseSession);
        } catch (Exception e) {
            log.error("Failed to save accounts", e);
            throw new RuntimeException("Failed to save accounts", e);
        }
    }
    
    /**
     * Save properties to file with backup
     */
    private void saveProperties() throws IOException {
        File propsFile = new File(propertiesPath);
        File backupFile = new File(propertiesPath + ".bak");
        
        // Create backup if file exists
        if (propsFile.exists()) {
            Files.copy(propsFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Write new properties file
        try (FileOutputStream fos = new FileOutputStream(propsFile)) {
            properties.store(fos, "TestCenter Configuration - Updated: " + new Date());
        } catch (IOException e) {
            log.error("Failed to save properties", e);
            
            // Restore from backup if it exists
            if (backupFile.exists()) {
                Files.copy(backupFile.toPath(), propsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Restored properties from backup after save failure");
            }
            
            throw e;
        }
    }
    
    /**
     * Update properties from accounts list
     */
    private void updatePropertiesFromAccounts() {
        // Remove all existing account properties
        Iterator<Object> iterator = properties.keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            if (key.startsWith(ACCOUNT_PREFIX)) {
                iterator.remove();
            }
        }
        
        // Add current accounts
        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO account = accounts.get(i);
            properties.setProperty(ACCOUNT_PREFIX + "[" + i + "]" + USERNAME_SUFFIX, account.getUsername());
            properties.setProperty(ACCOUNT_PREFIX + "[" + i + "]" + PASSWORD_SUFFIX, encryptPassword(account.getPassword()));
            properties.setProperty(ACCOUNT_PREFIX + "[" + i + "]" + ENABLED_SUFFIX, String.valueOf(account.isEnabled()));
        }
    }
    
    /**
     * Get the list of accounts
     */
    public ObservableList<AccountDTO> getAccounts() {
        return accounts;
    }
    
    /**
     * Add a new account
     */
    public void addAccount(AccountDTO account) {
        accounts.add(account);
        saveAccounts();
    }
    
    /**
     * Update an existing account
     */
    public void updateAccount(int index, AccountDTO updatedAccount) {
        if (index >= 0 && index < accounts.size()) {
            accounts.set(index, updatedAccount);
            saveAccounts();
        }
    }
    
    /**
     * Remove an account
     */
    public void removeAccount(int index) {
        if (index >= 0 && index < accounts.size()) {
            accounts.remove(index);
            saveAccounts();
        }
    }
    
    /**
     * Get path to properties file
     */
    public String getPropertiesPath() {
        return propertiesPath;
    }
    
    /**
     * Get the maximum number of navigation threads
     */
    public int getMaxThreads() {
        return maxThreads;
    }
    
    /**
     * Set the maximum number of navigation threads
     */
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        saveAccounts();
    }
    
    /**
     * Get the thread timeout in seconds
     */
    public int getThreadTimeout() {
        return DEFAULT_THREAD_TIMEOUT;
    }
    
    /**
     * Get the configured iteration count
     * @return the iteration count
     */
    public int getIterationCount() {
        return iterationCount;
    }
    
    /**
     * Set the iteration count
     * @param iterationCount the number of parsing iterations to perform
     */
    public void setIterationCount(int iterationCount) {
        if (iterationCount < 1) {
            this.iterationCount = 1;
        } else {
            this.iterationCount = iterationCount;
        }
    }
    
    /**
     * Encrypt a password
     */
    private String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        
        // Don't encrypt again if already encrypted
        if (password.startsWith(ENCRYPTED_PREFIX)) {
            return password;
        }
        
        return ENCRYPTED_PREFIX + passwordEncryptor.encryptPassword(password);
    }
    
    /**
     * Decrypt a password if needed
     */
    private String decryptPasswordIfNeeded(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        
        // Check if password is encrypted
        if (password.startsWith(ENCRYPTED_PREFIX)) {
            try {
                return passwordEncryptor.decryptPassword(password.substring(ENCRYPTED_PREFIX.length()));
            } catch (Exception e) {
                log.error("Failed to decrypt password", e);
                return "";
            }
        }
        
        return password;
    }
    
    /**
     * Get whether to reuse browser sessions between iterations
     * @return true if sessions should be reused, false otherwise
     */
    public boolean isReuseSession() {
        return reuseSession;
    }
    
    /**
     * Set whether to reuse browser sessions between iterations
     * @param reuseSession true to reuse sessions, false otherwise
     */
    public void setReuseSession(boolean reuseSession) {
        this.reuseSession = reuseSession;
        saveAccounts();
    }
    
    /**
     * DTO for account data
     */
    public static class AccountDTO {
        private String username;
        private String password;
        private boolean enabled;
        
        public AccountDTO() {
        }
        
        public AccountDTO(String username, String password, boolean enabled) {
            this.username = username;
            this.password = password;
            this.enabled = enabled;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
} 