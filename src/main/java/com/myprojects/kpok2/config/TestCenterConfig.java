package com.myprojects.kpok2.config;

import com.myprojects.kpok2.util.TestParserConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "testcenter")
public class TestCenterConfig {
    private String loginUrl = TestParserConstants.LOGIN_URL;
    private String username;
    private String password;
}