package com.myprojects.kpok2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.api.security")
public class ApiSecurityProperties {
    private String token = "";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(token);
    }
}
