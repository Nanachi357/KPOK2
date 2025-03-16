package com.myprojects.kpok2.config;

import com.myprojects.kpok2.util.TestParserConstants;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

/**
 * Legacy configuration class.
 * @deprecated Use TestCenterProperties instead, which provides extended functionality
 * and support for multiple accounts
 */
@Data
@Configuration
public class TestCenterConfig {
    private String loginUrl = TestParserConstants.LOGIN_URL;
    // These fields are kept for backward compatibility 
    // but should not be used for new development
    private String username;
    private String password;
}