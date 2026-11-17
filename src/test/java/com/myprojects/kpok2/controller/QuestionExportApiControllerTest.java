package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.service.QuestionExportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionExportApiControllerTest {

    @Test
    void returnsAttachmentWithSchemaHeaderAndNormalizedFilter() {
        QuestionExportService service = mock(QuestionExportService.class);
        QuestionExportDocument document = new QuestionExportDocument(
                QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-11-17T12:00:00Z"),
                new QuestionExportDocument.ExportFilter(
                        List.of(173L),
                        List.of("uk"),
                        List.of(QuestionType.SINGLE_CHOICE),
                        false,
                        true,
                        "case"),
                new QuestionExportDocument.ExportSummary(
                        0, 0, 0, 0, 0, 0, 0, 0),
                List.of(),
                List.of());
        when(service.buildExport(org.mockito.ArgumentMatchers.any()))
                .thenReturn(document);
        QuestionExportApiController controller =
                new QuestionExportApiController(service);

        var response = controller.export(
                Set.of(173L),
                Set.of("UK"),
                Set.of(QuestionType.SINGLE_CHOICE),
                false,
                true,
                " case ");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(document, response.getBody());
        assertEquals(
                QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                response.getHeaders().getFirst("X-KPOK2-Export-Schema"));
        assertTrue(response.getHeaders().getContentDisposition()
                .getFilename().endsWith(".json"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));

        ArgumentCaptor<QuestionExportFilter> filter =
                ArgumentCaptor.forClass(QuestionExportFilter.class);
        verify(service).buildExport(filter.capture());
        assertEquals(Set.of("uk"), filter.getValue().languages());
        assertEquals("case", filter.getValue().query());
    }
}
