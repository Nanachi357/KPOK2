package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.QuestionLibraryPageDto;
import com.myprojects.kpok2.model.dto.QuestionLibraryFilter;
import com.myprojects.kpok2.service.QuestionLibraryService;
import com.myprojects.kpok2.service.QuestionLibraryDetailService;
import com.myprojects.kpok2.model.dto.QuestionLibraryDetailDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
        QuestionLibraryController controller = new QuestionLibraryController(
                service, mock(QuestionLibraryDetailService.class));

        var response = controller.findQuestions(
                "cardiology", 173L, "uk", null, true, 2, 50);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(page, response.getBody());
        verify(service).findPage(filter, 2, 50);
    }

    @Test
    void returnsDetailOrNotFound() {
        QuestionLibraryDetailService detailService = mock(
                QuestionLibraryDetailService.class);
        QuestionLibraryDetailDto detail = new QuestionLibraryDetailDto(
                7L, "hash", "Context", "Question", null, List.of(), List.of());
        when(detailService.findByQuestionId(7L)).thenReturn(Optional.of(detail));
        when(detailService.findByQuestionId(99L)).thenReturn(Optional.empty());
        QuestionLibraryController controller = new QuestionLibraryController(
                mock(QuestionLibraryService.class), detailService);

        assertEquals(detail, controller.findQuestion(7L).getBody());
        assertEquals(404, controller.findQuestion(99L).getStatusCode().value());
    }
}
