package com.myprojects.kpok2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.api.import")
public class ApiImportProperties {
    public static final long DEFAULT_MAX_BYTES = 25L * 1024L * 1024L;

    private long maxBytes = DEFAULT_MAX_BYTES;

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }
}
