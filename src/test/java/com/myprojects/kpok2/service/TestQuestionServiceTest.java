package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapper;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestQuestionServiceTest {

    @Test
    void duplicateCanonicalQuestionStillRecordsVersionAndOrigin() {
        Fixture fixture = fixture();
        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder().build();
        TestQuestion parsed = TestQuestion.builder().questionHash("canonical-hash").build();
        TestQuestion existing = TestQuestion.builder()
                .id(42L)
                .questionHash("canonical-hash")
                .build();

        when(fixture.mapper.toEntity(dto)).thenReturn(parsed);
        when(fixture.repository.findByQuestionHash("canonical-hash"))
                .thenReturn(Optional.of(existing));
        stubExistingVersion(fixture, existing, dto);

        TestQuestionService.SaveResult result =
                fixture.service.saveQuestions(List.of(dto));

        assertTrue(result.newCanonicalQuestions().isEmpty());
        assertEquals(0, result.newQuestionRecords());

        verify(fixture.repository, never()).save(parsed);
        verify(fixture.originService).recordOrigin(existing, parsed);
        verify(fixture.versionService).recordVersion(existing, dto);
        verify(fixture.statistics).incrementNewQuestions(0);
    }

    @Test
    void listenerFailureDoesNotRollBackSavedQuestionOrVersion() {
        Fixture fixture = fixture();
        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder().build();
        TestQuestion entity = TestQuestion.builder().questionHash("hash").build();
        when(fixture.mapper.toEntity(dto)).thenReturn(entity);
        when(fixture.repository.findByQuestionHash("hash")).thenReturn(Optional.empty());
        when(fixture.repository.save(entity)).thenReturn(entity);
        when(fixture.versionService.recordVersion(entity, dto)).thenReturn(
                new QuestionVersionService.VersioningResult(
                        com.myprojects.kpok2.model.QuestionRevision.builder().id(1L).build(),
                        com.myprojects.kpok2.model.AnswerKey.builder().id(2L).build(),
                        true,
                        true,
                        false));
        when(fixture.versionService.toRecord(any(), any(), any())).thenReturn(
                new com.myprojects.kpok2.model.dto.QuestionRecordDto(
                        1L, 1L, 2L, null, "Question", List.of("answer"),
                        List.of("answer"), QuestionType.SINGLE_CHOICE, false));
        fixture.service.addListener(question -> {
            throw new IllegalStateException("UI is unavailable");
        });

        TestQuestionService.SaveResult saved = assertDoesNotThrow(
                () -> fixture.service.saveQuestions(List.of(dto)));

        assertEquals(List.of(entity), saved.newCanonicalQuestions());
        assertEquals(1, saved.newQuestionRecords());
        assertEquals(1, saved.newRevisions());
        assertEquals(1, saved.newAnswerKeys());
        verify(fixture.repository).save(entity);
        verify(fixture.originService).recordOrigin(entity, entity);
        verify(fixture.versionService).recordVersion(entity, dto);
        verify(fixture.statistics).incrementNewQuestions(1);
    }

    @Test
    void enrichesMissingRepresentativeMetadataOnDuplicate() {
        Fixture fixture = fixture();
        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder().build();
        TestQuestion parsed = TestQuestion.builder()
                .questionHash("canonical-hash")
                .sourceQuizId(173L)
                .sourceUrl("https://test.testcentr.org.ua/mod/quiz/view.php?id=173")
                .sourceLanguage("uk")
                .questionType(QuestionType.SINGLE_CHOICE)
                .contextText("Case")
                .normalizedContext("case")
                .build();
        TestQuestion existing = TestQuestion.builder()
                .id(42L)
                .questionHash("canonical-hash")
                .build();

        when(fixture.mapper.toEntity(dto)).thenReturn(parsed);
        when(fixture.repository.findByQuestionHash("canonical-hash"))
                .thenReturn(Optional.of(existing));
        stubExistingVersion(fixture, existing, dto);

        TestQuestionService.SaveResult result =
                fixture.service.saveQuestions(List.of(dto));

        assertTrue(result.newCanonicalQuestions().isEmpty());
        assertEquals(0, result.newQuestionRecords());

        assertEquals(173L, existing.getSourceQuizId());
        assertEquals(parsed.getSourceUrl(), existing.getSourceUrl());
        assertEquals("uk", existing.getSourceLanguage());
        assertEquals(QuestionType.SINGLE_CHOICE, existing.getQuestionType());
        assertEquals("Case", existing.getContextText());
        verify(fixture.repository).save(existing);
        verify(fixture.originService).recordOrigin(existing, parsed);
        verify(fixture.versionService).recordVersion(existing, dto);
    }

    @Test
    void countsNewRevisionOfExistingCanonicalAsNewQuestionRecord() {
        Fixture fixture = fixture();
        ParsedTestQuestionDto dto = ParsedTestQuestionDto.builder().build();
        TestQuestion parsed = TestQuestion.builder().questionHash("canonical-hash").build();
        TestQuestion existing = TestQuestion.builder()
                .id(42L)
                .questionHash("canonical-hash")
                .build();

        when(fixture.mapper.toEntity(dto)).thenReturn(parsed);
        when(fixture.repository.findByQuestionHash("canonical-hash"))
                .thenReturn(Optional.of(existing));
        when(fixture.versionService.recordVersion(existing, dto)).thenReturn(
                new QuestionVersionService.VersioningResult(
                        com.myprojects.kpok2.model.QuestionRevision.builder().id(1L).build(),
                        com.myprojects.kpok2.model.AnswerKey.builder().id(2L).build(),
                        true,
                        true,
                        false));
        when(fixture.versionService.toRecord(any(), any(), any())).thenReturn(
                new com.myprojects.kpok2.model.dto.QuestionRecordDto(
                        42L, 1L, 2L, null, "Question", List.of("answer"),
                        List.of("answer"), QuestionType.SINGLE_CHOICE, false));

        TestQuestionService.SaveResult result =
                fixture.service.saveQuestions(List.of(dto));

        assertTrue(result.newCanonicalQuestions().isEmpty());
        assertEquals(1, result.newQuestionRecords());
        assertEquals(1, result.newRevisions());
        assertEquals(1, result.newAnswerKeys());
        verify(fixture.statistics).incrementNewQuestions(1);
    }

    private Fixture fixture() {
        TestQuestionRepository repository = mock(TestQuestionRepository.class);
        TestQuestionMapper mapper = mock(TestQuestionMapper.class);
        TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        QuestionOriginService originService = mock(QuestionOriginService.class);
        QuestionVersionService versionService = mock(QuestionVersionService.class);
        TestQuestionService service = new TestQuestionService(
                repository,
                mapper,
                statistics,
                originService,
                versionService);
        return new Fixture(
                service,
                repository,
                mapper,
                statistics,
                originService,
                versionService);
    }

    private void stubExistingVersion(
            Fixture fixture, TestQuestion question, ParsedTestQuestionDto dto) {
        when(fixture.versionService.recordVersion(question, dto)).thenReturn(
                new QuestionVersionService.VersioningResult(
                        com.myprojects.kpok2.model.QuestionRevision.builder().id(1L).build(),
                        com.myprojects.kpok2.model.AnswerKey.builder().id(2L).build(),
                        false,
                        false,
                        false));
    }

    private record Fixture(
            TestQuestionService service,
            TestQuestionRepository repository,
            TestQuestionMapper mapper,
            TestParsingStatistics statistics,
            QuestionOriginService originService,
            QuestionVersionService versionService) {
    }
}
