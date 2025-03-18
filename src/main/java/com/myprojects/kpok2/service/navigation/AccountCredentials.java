package com.myprojects.kpok2.service.navigation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores account credentials for TestCenter authentication.
 * Each account will be assigned to a separate thread for parallel processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountCredentials {
    private String username;
    private String password;
    private boolean inUse = false;
    
    /**
     * Create new credentials with the given username and password
     */
    public AccountCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }
} 