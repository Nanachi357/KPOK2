package com.myprojects.kpok2.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultNetworkBindingTest {

    @Test
    void apiDefaultsToHostLoopback() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(input);
            properties.load(input);
        }

        assertEquals("${SERVER_ADDRESS:127.0.0.1}", properties.getProperty("server.address"));
        assertEquals("${KPOK2_API_TOKEN:}",
                properties.getProperty("app.api.security.token"));
        assertEquals("${KPOK2_API_IMPORT_MAX_BYTES:26214400}",
                properties.getProperty("app.api.import.max-bytes"));
    }
}
