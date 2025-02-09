package com.myprojects.kpok2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "testcenter")
public class TestCenterConfig {
    private String loginUrl = "https://test-center.ontu.edu.ua/site/login";
    private String username;
    private String password;
}