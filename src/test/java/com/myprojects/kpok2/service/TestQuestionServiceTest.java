package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapper;
import com.myprojects.kpok2.service.mapper.TestQuestionMapperUtils;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import com.myprojects.kpok2.util.JsonConverter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestQuestionServiceTest {

    @Test
    void checksV2AndLegacyHashesBeforeSaving() {
        TestQuestionRepository repository = mock(TestQuestionRepository.class);
        TestQuestionMapper mapper = mock(TestQuestionMapper.class);
        TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        TestQuestionService service = new TestQuestionService(
                repository, mapper, statistics, mock(JsonConverter.class));

        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder()
                .answers(List.of("a. Три", "б. Чотири"))
                .legacyAnswers(List.of("a. Три", "б. Чотири"))
                .legacyNormalizedText("скільки буде два плюс два")
                .legacyNormalizedCorrectAnswer("б чотири")
                .build();
        when(mapper.toEntity(dto)).thenReturn(
                TestQuestion.builder().questionHash("v2-hash").build());
        when(repository.existsByQuestionHashIn(anyCollection())).thenReturn(true);

        assertTrue(service.saveUniqueQuestions(List.of(dto)).isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> hashes = ArgumentCaptor.forClass(Collection.class);
        verify(repository).existsByQuestionHashIn(hashes.capture());
        assertEquals(2, hashes.getValue().size());
        assertTrue(hashes.getValue().contains("v2-hash"));
        assertTrue(hashes.getValue().contains(TestQuestionMapperUtils.generateLegacyHash(
                dto.getLegacyNormalizedText(), dto.getLegacyAnswers(), dto.getLegacyNormalizedCorrectAnswer())));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listenerFailureDoesNotRollBackSavedQuestion() {
        TestQuestionRepository repository = mock(TestQuestionRepository.class);
        TestQuestionMapper mapper = mock(TestQuestionMapper.class);
        TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        JsonConverter jsonConverter = mock(JsonConverter.class);
        TestQuestionService service = new TestQuestionService(
                repository, mapper, statistics, jsonConverter);

        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder().build();
        TestQuestion entity = TestQuestion.builder().questionHash("hash").build();
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(jsonConverter.getAnswersFromJson(entity)).thenReturn(List.of("answer"));
        service.addListener((question, answers) -> {
            throw new IllegalStateException("UI is unavailable");
        });

        List<TestQuestion> saved = assertDoesNotThrow(
                () -> service.saveUniqueQuestions(List.of(dto)));

        assertEquals(List.of(entity), saved);
        verify(repository).save(entity);
        verify(statistics).incrementNewQuestions(1);
    }

    @Test
    void enrichesMissingProvenanceOnLegacyDuplicate() {
        TestQuestionRepository repository = mock(TestQuestionRepository.class);
        TestQuestionMapper mapper = mock(TestQuestionMapper.class);
        TestQuestionService service = new TestQuestionService(
                repository, mapper, mock(TestParsingStatistics.class), mock(JsonConverter.class));

        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder()
                .legacyNormalizedText("legacy text")
                .legacyNormalizedCorrectAnswer("legacy answer")
                .legacyAnswers(List.of("a. answer"))
                .build();
        TestQuestion parsed = TestQuestion.builder()
                .questionHash("v2-hash")
                .sourceQuizId(173L)
                .sourceUrl("https://test.testcentr.org.ua/mod/quiz/view.php?id=173")
                .sourceLanguage("uk")
                .questionType(QuestionType.SINGLE_CHOICE)
                .build();
        TestQuestion existing = TestQuestion.builder()
                .questionHash("legacy-hash")
                .build();

        when(mapper.toEntity(dto)).thenReturn(parsed);
        when(repository.existsByQuestionHashIn(anyCollection())).thenReturn(true);
        when(repository.findFirstByQuestionHashIn(anyCollection()))
                .thenReturn(Optional.of(existing));

        assertTrue(service.saveUniqueQuestions(List.of(dto)).isEmpty());

        assertEquals(173L, existing.getSourceQuizId());
        assertEquals(parsed.getSourceUrl(), existing.getSourceUrl());
        assertEquals("uk", existing.getSourceLanguage());
        assertEquals(QuestionType.SINGLE_CHOICE, existing.getQuestionType());
        verify(repository).save(existing);
    }
}
