package com.myprojects.kpok2.config;

import lombok.Getter;
import org.springframework.stereotype.Component;
import java.time.Duration;

/**
 * Centralized management of all timeouts in the system.
 * All timeouts are configured through presets and available via this class.
 */
@Component
@Getter
public class TimeoutSettings {
    private final TimeoutConfig timeoutConfig;
    
    public TimeoutSettings(TimeoutConfig timeoutConfig) {
        this.timeoutConfig = timeoutConfig;
    }
    
    /**
     * Page load timeout in WebDriver
     */
    public Duration getPageLoadTimeout() {
        return Duration.ofSeconds(timeoutConfig.getPageLoadTimeout());
    }
    
    /**
     * Script execution timeout
     */
    public Duration getScriptTimeout() {
        return Duration.ofSeconds(timeoutConfig.getNavigationTimeout());
    }
    
    /**
     * Implicit timeout for element search
     */
    public Duration getImplicitWait() {
        return Duration.ofSeconds(timeoutConfig.getElementTimeout());
    }
    
    /**
     * Timeout for WebDriverWait
     */
    public Duration getExplicitWait() {
        return Duration.ofSeconds(timeoutConfig.getElementTimeout());
    }
    
    /**
     * Timeout for waiting elements during authentication
     */
    public Duration getAuthenticationWait() {
        return Duration.ofSeconds(timeoutConfig.getNavigationTimeout());
    }
    
    /**
     * Timeout for navigation between pages
     */
    public Duration getNavigationWait() {
        return Duration.ofSeconds(timeoutConfig.getNavigationTimeout());
    }
    
    /**
     * Get current preset
     */
    public TimeoutPreset getCurrentPreset() {
        return timeoutConfig.getCurrentPreset();
    }
    
    /**
     * Set new preset
     */
    public void setPreset(TimeoutPreset preset) {
        timeoutConfig.setPreset(preset);
    }
} 