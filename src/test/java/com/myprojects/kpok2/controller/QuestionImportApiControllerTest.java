package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.importing.QuestionImportPreview;
import com.myprojects.kpok2.service.QuestionImportService;
import com.myprojects.kpok2.service.QuestionImportServiceTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionImportApiControllerTest {

    @Test
    void delegatesReadOnlyPreviewWithoutStartingAnImport() {
        QuestionImportService service = mock(QuestionImportService.class);
        var document = QuestionImportServiceTest.validDocument();
        QuestionImportPreview preview = new QuestionImportPreview(
                document.schemaVersion(),
                document.generatedAt(),
                document.summary(),
                false,
                java.util.List.of());
        when(service.inspect(document)).thenReturn(preview);
        QuestionImportApiController controller =
                new QuestionImportApiController(service);

        assertEquals(preview, controller.preview(document));
        verify(service).inspect(document);
    }
}
