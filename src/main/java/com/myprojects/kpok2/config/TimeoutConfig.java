package com.myprojects.kpok2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class TimeoutConfig {
    private TimeoutPreset currentPreset = TimeoutPreset.DEFAULT;
    
    public void setPreset(TimeoutPreset preset) {
        this.currentPreset = preset;
    }
    
    public int getPageLoadTimeout() {
        return currentPreset.getPageLoadTimeoutSeconds();
    }
    
    public int getNavigationTimeout() {
        return currentPreset.getNavigationTimeoutSeconds();
    }
    
    public int getElementTimeout() {
        return currentPreset.getElementTimeoutSeconds();
    }
} 