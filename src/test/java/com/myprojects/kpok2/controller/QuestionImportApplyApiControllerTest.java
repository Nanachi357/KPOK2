package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import com.myprojects.kpok2.service.QuestionImportPersistenceService;
import com.myprojects.kpok2.service.QuestionImportServiceTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionImportApplyApiControllerTest {

    @Test
    void delegatesTheExplicitMutationEndpoint() {
        QuestionImportPersistenceService service = mock(
                QuestionImportPersistenceService.class);
        var document = QuestionImportServiceTest.validDocument();
        QuestionImportResult result = new QuestionImportResult(
                document.schemaVersion(),
                1, 0,
                1, 0,
                1, 0,
                1, 0,
                1, 0,
                1, 0,
                0);
        when(service.importDocument(document)).thenReturn(result);
        QuestionImportApplyApiController controller =
                new QuestionImportApplyApiController(service);

        assertEquals(result, controller.apply(document));
        verify(service).importDocument(document);
    }
}
