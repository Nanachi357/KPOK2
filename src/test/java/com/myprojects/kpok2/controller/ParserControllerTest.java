package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.QuestionAsset;
import com.myprojects.kpok2.service.QuestionAssetService;
import com.myprojects.kpok2.service.TestQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParserControllerTest {

    @Test
    void servesStoredImageWithImmutableAndSafeHeaders() {
        QuestionAssetService assetService = mock(QuestionAssetService.class);
        byte[] content = new byte[]{1, 2, 3};
        when(assetService.findById(7L)).thenReturn(Optional.of(
                QuestionAsset.builder()
                        .id(7L)
                        .contentHash("0123456789abcdef")
                        .mediaType("image/png")
                        .originalFileName("diagram.png")
                        .contentLength(content.length)
                        .content(content)
                        .build()));
        ParserController controller = new ParserController(
                mock(TestQuestionService.class),
                assetService);

        var response = controller.getQuestionAsset(7L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals("nosniff", response.getHeaders().getFirst(
                "X-Content-Type-Options"));
        assertNotNull(response.getHeaders().getFirst("Content-Security-Policy"));
        assertNotNull(response.getHeaders().getCacheControl());
        assertArrayEquals(content, response.getBody());
    }

    @Test
    void returnsNotFoundForUnknownImage() {
        QuestionAssetService assetService = mock(QuestionAssetService.class);
        when(assetService.findById(99L)).thenReturn(Optional.empty());
        ParserController controller = new ParserController(
                mock(TestQuestionService.class),
                assetService);

        assertEquals(404, controller.getQuestionAsset(99L).getStatusCode().value());
    }

    @Test
    void legacyParseEndpointsAreNotMapped() throws Exception {
        ParserController controller = new ParserController(
                mock(TestQuestionService.class),
                mock(QuestionAssetService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/parse").param("url", "https://example.test/quiz"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/parse/results"))
                .andExpect(status().isNotFound());
    }
}
