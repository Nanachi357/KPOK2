package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionAsset;
import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.QuestionRevisionAsset;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedQuestionAssetDto;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.QuestionAssetRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionRevisionAssetRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.util.JsonConverter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionVersionServiceTest {

    @Test
    void createsRevisionKeyAndAttemptObservation() {
        Fixture fixture = fixture();
        TestQuestion question = canonicalQuestion();
        ParsedTestQuestionDto parsed = parsedQuestion();
        when(fixture.revisions.findByQuestionIdAndRevisionHash(any(), any()))
                .thenReturn(Optional.empty());
        when(fixture.revisions.save(any())).thenAnswer(invocation -> {
            QuestionRevision revision = invocation.getArgument(0);
            revision.setId(10L);
            return revision;
        });
        when(fixture.keys.findByRevisionIdAndKeyHash(any(), any()))
                .thenReturn(Optional.empty());
        when(fixture.keys.countByRevisionId(10L)).thenReturn(0L);
        when(fixture.keys.save(any())).thenAnswer(invocation -> {
            AnswerKey key = invocation.getArgument(0);
            key.setId(20L);
            return key;
        });
        when(fixture.observations
                .findByRevisionIdAndAnswerKeyIdAndQuizIdAndSourceAttemptIdAndLanguage(
                        10L, 20L, 173L, "456", "uk"))
                .thenReturn(Optional.empty());

        var result = fixture.service.recordVersion(question, parsed);

        assertTrue(result.newRevision());
        assertTrue(result.newAnswerKey());
        assertFalse(result.answerKeyConflict());

        ArgumentCaptor<QuestionObservation> observation =
                ArgumentCaptor.forClass(QuestionObservation.class);
        verify(fixture.observations).save(observation.capture());
        assertEquals(question, observation.getValue().getQuestion());
        assertEquals("456", observation.getValue().getSourceAttemptId());
        assertEquals(1, observation.getValue().getObservationCount());
    }

    @Test
    void flagsNewKeyForExistingRevisionAsConflict() {
        Fixture fixture = fixture();
        TestQuestion question = canonicalQuestion();
        ParsedTestQuestionDto parsed = parsedQuestion();
        QuestionRevision revision = QuestionRevision.builder()
                .id(10L)
                .question(question)
                .revisionHash("revision")
                .build();
        when(fixture.revisions.findByQuestionIdAndRevisionHash(any(), any()))
                .thenReturn(Optional.of(revision));
        when(fixture.keys.findByRevisionIdAndKeyHash(any(), any()))
                .thenReturn(Optional.empty());
        when(fixture.keys.countByRevisionId(10L)).thenReturn(1L);
        when(fixture.keys.save(any())).thenAnswer(invocation -> {
            AnswerKey key = invocation.getArgument(0);
            key.setId(20L);
            return key;
        });

        var result = fixture.service.recordVersion(question, parsed);

        assertFalse(result.newRevision());
        assertTrue(result.newAnswerKey());
        assertTrue(result.answerKeyConflict());
    }

    @Test
    void repeatedObservationUpdatesCounterWithoutCreatingVersions() {
        Fixture fixture = fixture();
        TestQuestion question = canonicalQuestion();
        ParsedTestQuestionDto parsed = parsedQuestion();
        QuestionRevision revision = QuestionRevision.builder()
                .id(10L)
                .question(question)
                .revisionHash("revision")
                .build();
        AnswerKey key = AnswerKey.builder()
                .id(20L)
                .revision(revision)
                .keyHash("key")
                .build();
        QuestionObservation observation = QuestionObservation.builder()
                .id(30L)
                .question(question)
                .revision(revision)
                .answerKey(key)
                .quizId(173L)
                .quizUrl(parsed.getSourceUrl())
                .sourceAttemptId("456")
                .language("uk")
                .observationCount(1)
                .build();
        when(fixture.revisions.findByQuestionIdAndRevisionHash(any(), any()))
                .thenReturn(Optional.of(revision));
        when(fixture.keys.findByRevisionIdAndKeyHash(any(), any()))
                .thenReturn(Optional.of(key));
        when(fixture.observations
                .findByRevisionIdAndAnswerKeyIdAndQuizIdAndSourceAttemptIdAndLanguage(
                        10L, 20L, 173L, "456", "uk"))
                .thenReturn(Optional.of(observation));

        var result = fixture.service.recordVersion(question, parsed);

        assertFalse(result.newRevision());
        assertFalse(result.newAnswerKey());
        assertFalse(result.answerKeyConflict());
        assertEquals(2, observation.getObservationCount());
        verify(fixture.observations).save(observation);
    }

    @Test
    void queryReturnsEveryRevisionAndConflictingKey() {
        Fixture fixture = fixture();
        TestQuestion question = canonicalQuestion();
        QuestionRevision revision = QuestionRevision.builder()
                .id(10L)
                .question(question)
                .contextText("Case")
                .questionText("Question")
                .possibleAnswersJson("options")
                .questionType(QuestionType.SINGLE_CHOICE)
                .build();
        AnswerKey first = AnswerKey.builder()
                .id(20L)
                .revision(revision)
                .correctAnswersJson("first-key")
                .build();
        AnswerKey second = AnswerKey.builder()
                .id(21L)
                .revision(revision)
                .correctAnswersJson("second-key")
                .build();
        QuestionAsset asset = QuestionAsset.builder()
                .id(30L)
                .contentHash("content-hash")
                .mediaType("image/png")
                .originalFileName("diagram.png")
                .contentLength(3)
                .build();
        QuestionRevisionAsset assetReference = QuestionRevisionAsset.builder()
                .id(40L)
                .revision(revision)
                .asset(asset)
                .contentLocation(QuestionContentLocation.QUESTION)
                .answerIndex(-1)
                .sequenceIndex(0)
                .sourceUrl("https://moodle.example/diagram.png")
                .altText("Diagram")
                .fetchStatus(AssetFetchStatus.STORED)
                .build();
        when(fixture.revisions.findAllByOrderByQuestionIdAscIdAsc())
                .thenReturn(List.of(revision));
        when(fixture.keys.findAllByRevisionIdInOrderByRevisionIdAscIdAsc(
                List.of(10L)))
                .thenReturn(List.of(first, second));
        when(fixture.assetReferences
                .findAllByRevisionIdInOrderByRevisionIdAscContentLocationAscAnswerIndexAscSequenceIndexAsc(
                        List.of(10L)))
                .thenReturn(List.of(assetReference));
        when(fixture.jsonConverter.getAnswersFromJson("options"))
                .thenReturn(List.of("a. Alpha", "b. Beta"));
        when(fixture.jsonConverter.getAnswersFromJson("first-key"))
                .thenReturn(List.of("a. Alpha"));
        when(fixture.jsonConverter.getAnswersFromJson("second-key"))
                .thenReturn(List.of("b. Beta"));

        var records = fixture.service.getAllRecords();

        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(record -> record.answerKeyConflict()));
        assertEquals(List.of("a. Alpha"), records.get(0).correctAnswers());
        assertEquals(List.of("b. Beta"), records.get(1).correctAnswers());
        assertEquals(1, records.get(0).assets().size());
        assertEquals("/api/assets/30", records.get(0).assets().get(0).contentUrl());
    }

    @Test
    void storesRichContentAndDeduplicatedImagePlacement() {
        Fixture fixture = fixture();
        TestQuestion question = canonicalQuestion();
        ParsedTestQuestionDto parsed = parsedQuestion();
        parsed.setContextHtml("<p><strong>Case</strong></p>");
        parsed.setQuestionHtml("<p>Question <img src=\"kpok2-asset:hash\"></p>");
        parsed.setAnswerHtml(List.of("<span>Alpha</span>", "<span>Beta</span>"));
        parsed.setRevisionContentFingerprint("answer-content");
        parsed.setAssets(List.of(ParsedQuestionAssetDto.builder()
                .referenceId("asset-0")
                .contentLocation(QuestionContentLocation.QUESTION)
                .answerIndex(-1)
                .sequenceIndex(0)
                .sourceUrl("embedded:hash")
                .altText("Diagram")
                .contentHash("hash")
                .mediaType("image/png")
                .originalFileName("diagram.png")
                .content(new byte[]{1, 2, 3})
                .fetchStatus(AssetFetchStatus.STORED)
                .build()));
        when(fixture.revisions.findByQuestionIdAndRevisionHash(any(), any()))
                .thenReturn(Optional.empty());
        when(fixture.revisions.save(any())).thenAnswer(invocation -> {
            QuestionRevision revision = invocation.getArgument(0);
            revision.setId(10L);
            return revision;
        });
        when(fixture.keys.findByRevisionIdAndKeyHash(any(), any()))
                .thenReturn(Optional.empty());
        when(fixture.keys.save(any())).thenAnswer(invocation -> {
            AnswerKey key = invocation.getArgument(0);
            key.setId(20L);
            return key;
        });
        when(fixture.assets.findByContentHash("hash")).thenReturn(Optional.empty());
        when(fixture.assets.save(any())).thenAnswer(invocation -> {
            QuestionAsset asset = invocation.getArgument(0);
            asset.setId(30L);
            return asset;
        });
        when(fixture.assetReferences.findByRevisionIdAndReferenceKey(
                10L, "QUESTION:-1:0"))
                .thenReturn(Optional.empty());

        fixture.service.recordVersion(question, parsed);

        ArgumentCaptor<QuestionRevision> revision =
                ArgumentCaptor.forClass(QuestionRevision.class);
        verify(fixture.revisions).save(revision.capture());
        assertEquals(parsed.getQuestionHtml(), revision.getValue().getQuestionHtml());
        assertEquals("[\"<span>Alpha</span>\",\"<span>Beta</span>\"]",
                revision.getValue().getAnswerHtmlJson());

        ArgumentCaptor<QuestionRevisionAsset> reference =
                ArgumentCaptor.forClass(QuestionRevisionAsset.class);
        verify(fixture.assetReferences).save(reference.capture());
        assertEquals(30L, reference.getValue().getAsset().getId());
        assertEquals("QUESTION:-1:0", reference.getValue().getReferenceKey());
        assertEquals(AssetFetchStatus.STORED, reference.getValue().getFetchStatus());
    }

    private Fixture fixture() {
        QuestionRevisionRepository revisions = mock(QuestionRevisionRepository.class);
        AnswerKeyRepository keys = mock(AnswerKeyRepository.class);
        QuestionObservationRepository observations = mock(QuestionObservationRepository.class);
        QuestionAssetRepository assets = mock(QuestionAssetRepository.class);
        QuestionRevisionAssetRepository assetReferences =
                mock(QuestionRevisionAssetRepository.class);
        JsonConverter jsonConverter = mock(JsonConverter.class);
        return new Fixture(
                new QuestionVersionService(
                        revisions,
                        keys,
                        observations,
                        assets,
                        assetReferences,
                        jsonConverter),
                revisions,
                keys,
                observations,
                assets,
                assetReferences,
                jsonConverter);
    }

    private TestQuestion canonicalQuestion() {
        return TestQuestion.builder()
                .id(1L)
                .questionHash("canonical")
                .build();
    }

    private ParsedTestQuestionDto parsedQuestion() {
        return ParsedTestQuestionDto.builder()
                .contextText("Case")
                .questionText("Question")
                .answers(List.of("a. Alpha", "b. Beta"))
                .normalizedAnswers(List.of("alpha", "beta"))
                .correctAnswers(List.of("b. Beta"))
                .normalizedCorrectAnswers(List.of("beta"))
                .questionType(QuestionType.SINGLE_CHOICE)
                .sourceQuizId(173L)
                .sourceUrl("https://test.testcentr.org.ua/mod/quiz/view.php?id=173")
                .sourceLanguage("uk")
                .sourceAttemptId("456")
                .build();
    }

    private record Fixture(
            QuestionVersionService service,
            QuestionRevisionRepository revisions,
            AnswerKeyRepository keys,
            QuestionObservationRepository observations,
            QuestionAssetRepository assets,
            QuestionRevisionAssetRepository assetReferences,
            JsonConverter jsonConverter) {
    }
}
