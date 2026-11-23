package com.myprojects.kpok2.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebAppConfiguration
@SpringJUnitConfig(classes = {
        ApiSecurityConfig.class,
        ApiSecurityConfigTest.TestWebConfig.class
})
@TestPropertySource(properties = {
        "app.api.security.token=api-test-token-with-at-least-32-characters"
})
class ApiSecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void rejectsMissingCredentials() throws Exception {
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(get("/api/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsConfiguredCredentials() throws Exception {
        mockMvc.perform(get("/api/probe")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer api-test-token-with-at-least-32-characters"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void acceptsAuthenticatedStatelessPostWithoutBrowserCsrfToken() throws Exception {
        mockMvc.perform(post("/api/probe")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer api-test-token-with-at-least-32-characters"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void rejectsConfiguredTokenThatIsTooShort() {
        ApiSecurityProperties properties = new ApiSecurityProperties();
        properties.setToken("too-short");
        ApiSecurityConfig config = new ApiSecurityConfig(properties);

        assertThrows(IllegalStateException.class, config::apiTokenAuthenticationFilter);
    }

    @RestController
    @RequestMapping("/api")
    static class ProbeController {
        @GetMapping("/probe")
        ResponseEntity<String> probe() {
            return ResponseEntity.ok("ok");
        }

        @PostMapping("/probe")
        ResponseEntity<String> updateProbe() {
            return ResponseEntity.ok("ok");
        }
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }
}
