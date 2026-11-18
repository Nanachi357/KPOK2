package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.QuestionLibraryPageDto;
import com.myprojects.kpok2.model.dto.QuestionLibraryFilter;
import com.myprojects.kpok2.service.QuestionLibraryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionLibraryControllerTest {

    @Test
    void delegatesPaginationAndSearchToLibraryService() {
        QuestionLibraryService service = mock(QuestionLibraryService.class);
        QuestionLibraryPageDto page = new QuestionLibraryPageDto(
                List.of(), 2, 50, 120, 3, false, true);
        QuestionLibraryFilter filter = new QuestionLibraryFilter(
                "cardiology", 173L, "uk", null, true);
        when(service.findPage(filter, 2, 50)).thenReturn(page);
        QuestionLibraryController controller = new QuestionLibraryController(service);

        var response = controller.findQuestions(
                "cardiology", 173L, "uk", null, true, 2, 50);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(page, response.getBody());
        verify(service).findPage(filter, 2, 50);
    }
}
