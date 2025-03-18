package com.myprojects.kpok2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration class for debug settings
 */
@Component
@ConfigurationProperties(prefix = "app.debug")
@Data
public class DebugProperties {
    
    /**
     * Whether to save HTML files and screenshots for debugging
     */
    private boolean saveFiles = false;
    
    /**
     * Directory for debug output
     */
    private String outputDir = "debug_output";
    
    /**
     * Subdirectory for HTML files
     */
    private String htmlDir = "html";
    
    /**
     * Subdirectory for screenshots
     */
    private String screenshotsDir = "screenshots";
    
    /**
     * Get full path to HTML directory
     */
    public String getHtmlDirPath() {
        return outputDir + "/" + htmlDir;
    }
    
    /**
     * Get full path to screenshots directory
     */
    public String getScreenshotsDirPath() {
        return outputDir + "/" + screenshotsDir;
    }
} 