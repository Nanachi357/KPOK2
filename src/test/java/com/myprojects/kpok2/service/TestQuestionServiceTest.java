package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
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
}
