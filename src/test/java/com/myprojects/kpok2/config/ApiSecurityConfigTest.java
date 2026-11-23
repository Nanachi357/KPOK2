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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        "app.api.security.token=api-test-token-with-at-least-32-characters",
        "app.api.import.max-bytes=64"
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
    void rejectsOversizedImportBeforeControllerInvocation() throws Exception {
        mockMvc.perform(post("/api/imports/questions")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer api-test-token-with-at-least-32-characters")
                        .contentType("application/json")
                        .content("x".repeat(65)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void authenticationRunsBeforePayloadLimit() throws Exception {
        mockMvc.perform(post("/api/imports/questions")
                        .contentType("application/json")
                        .content("x".repeat(65)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOversizedChunkedBodyWithoutContentLength() throws Exception {
        ApiImportPayloadLimitFilter filter = new ApiImportPayloadLimitFilter(64);
        AtomicBoolean downstreamCompleted = new AtomicBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setMethod("POST");
        request.setRequestURI("/api/imports/questions");
        request.setContent("x".repeat(65).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (limitedRequest, ignoredResponse) -> {
            limitedRequest.getInputStream().readAllBytes();
            downstreamCompleted.set(true);
        });

        assertEquals(413, response.getStatus());
        assertFalse(downstreamCompleted.get());
    }

    @Test
    void acceptsImportWithinConfiguredLimit() throws Exception {
        mockMvc.perform(post("/api/imports/questions")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer api-test-token-with-at-least-32-characters")
                        .contentType("application/json")
                        .content("{}"))
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

    @RestController
    @RequestMapping("/api/imports/questions")
    static class ImportProbeController {
        @PostMapping
        ResponseEntity<String> importDocument(@RequestBody String ignored) {
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

        @Bean
        ImportProbeController importProbeController() {
            return new ImportProbeController();
        }
    }
}
