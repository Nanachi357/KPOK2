package com.myprojects.kpok2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for enabling TestCenterProperties
 */
@Configuration
@EnableConfigurationProperties(TestCenterProperties.class)
public class TestCenterPropertiesConfig {
    // Empty class, just for enabling @ConfigurationProperties
} 