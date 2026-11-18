package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.QuestionRecordDto;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionLibraryServiceTest {

    @Test
    void returnsCanonicalPageWithRevisionAndKeySummary() {
        TestQuestionRepository questions = mock(TestQuestionRepository.class);
        QuestionVersionService versions = mock(QuestionVersionService.class);
        QuestionLibraryService service = new QuestionLibraryService(questions, versions);
        TestQuestion question = TestQuestion.builder()
                .id(7L)
                .questionText("Canonical wording")
                .contextText("Clinical context")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .build();
        PageRequest request = PageRequest.of(0, 25);
        when(questions.findLibraryPage("kidney", request)).thenReturn(
                new PageImpl<>(List.of(question), request, 41));
        when(versions.getRecordsForQuestions(List.of(7L))).thenReturn(List.of(
                record(7L, 10L, 100L, false),
                record(7L, 10L, 101L, true),
                record(7L, 11L, 102L, false)));

        var result = service.findPage("  kidney  ", 0, 25);

        assertEquals(1, result.items().size());
        assertEquals(41, result.totalItems());
        assertEquals(2, result.totalPages());
        assertTrue(result.first());
        assertFalse(result.last());
        var item = result.items().get(0);
        assertEquals(7L, item.questionId());
        assertEquals("Clinical context", item.contextText());
        assertEquals("Canonical wording", item.questionText());
        assertEquals(2, item.revisionCount());
        assertEquals(3, item.answerKeyCount());
        assertTrue(item.answerKeyConflict());
    }

    @Test
    void normalizesInvalidPageAndCapsPageSize() {
        TestQuestionRepository questions = mock(TestQuestionRepository.class);
        QuestionVersionService versions = mock(QuestionVersionService.class);
        QuestionLibraryService service = new QuestionLibraryService(questions, versions);
        when(questions.findLibraryPage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(Page.empty());

        service.findPage(null, -5, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(questions).findLibraryPage(
                org.mockito.ArgumentMatchers.eq(""), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(QuestionLibraryService.MAX_PAGE_SIZE,
                pageable.getValue().getPageSize());
    }

    private QuestionRecordDto record(
            Long questionId,
            Long revisionId,
            Long keyId,
            boolean conflict) {
        return new QuestionRecordDto(
                questionId,
                revisionId,
                keyId,
                "Fallback context",
                "Fallback wording",
                List.of("A", "B"),
                List.of("A"),
                QuestionType.MULTIPLE_CHOICE,
                conflict);
    }
}
