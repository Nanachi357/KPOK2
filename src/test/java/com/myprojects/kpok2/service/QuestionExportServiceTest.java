package com.myprojects.kpok2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.CollectionAttempt;
import com.myprojects.kpok2.model.QuestionAsset;
import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.export.QuestionAssetExportMetadata;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.CollectionAttemptRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import com.myprojects.kpok2.repository.QuestionRevisionAssetRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.util.JsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsFilteredConflictWithRichContentOriginsAttemptsAndAssets() {
        Fixture fixture = fixture();
        fixture.stubCompleteQuestion();
        when(fixture.attempts.findAllByQuizIdInOrderByQuizIdAscIdAsc(Set.of(173L)))
                .thenReturn(List.of(fixture.attempt));

        QuestionExportDocument document = fixture.service.buildExport(
                new QuestionExportFilter(
                        Set.of(173L),
                        Set.of("UK"),
                        Set.of(QuestionType.SINGLE_CHOICE),
                        true,
                        true,
                        "diagram"));

        assertEquals(QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                document.schemaVersion());
        assertNotNull(document.generatedAt());
        assertEquals(1, document.questions().size());
        assertEquals(1, document.attempts().size());
        assertEquals(1, document.summary().canonicalQuestions());
        assertEquals(1, document.summary().revisions());
        assertEquals(2, document.summary().answerKeys());
        assertEquals(1, document.summary().conflicts());
        assertEquals(2, document.summary().observations());
        assertEquals(1, document.summary().assets());

        var revision = document.questions().get(0).revisions().get(0);
        assertTrue(revision.answerKeyConflict());
        assertEquals("<p>Question <strong>HTML</strong></p>",
                revision.questionHtml());
        assertEquals("/api/assets/40", revision.assets().get(0).contentUrl());
        assertEquals("diagram.png", revision.assets().get(0).originalFileName());
    }

    @Test
    void languageFilterExcludesQuestionsAndAttemptHistoryOutsideScope() {
        Fixture fixture = fixture();
        fixture.stubCompleteQuestion();

        QuestionExportDocument document = fixture.service.buildExport(
                new QuestionExportFilter(
                        Set.of(173L),
                        Set.of("en"),
                        Set.of(),
                        false,
                        true,
                        ""));

        assertTrue(document.questions().isEmpty());
        assertTrue(document.attempts().isEmpty());
        verify(fixture.attempts, never())
                .findAllByQuizIdInOrderByQuizIdAscIdAsc(any());
    }

    @Test
    void writesValidPrettyJsonWithoutEmbeddingAssetBinary() throws Exception {
        Fixture fixture = fixture();
        fixture.stubCompleteQuestion();
        Path target = tempDir.resolve("questions.json");

        QuestionExportDocument.ExportSummary summary = fixture.service.writeJson(
                target,
                new QuestionExportFilter(
                        Set.of(173L),
                        Set.of("uk"),
                        Set.of(),
                        false,
                        false,
                        ""));

        assertEquals(1, summary.canonicalQuestions());
        assertTrue(Files.exists(target));
        String json = Files.readString(target);
        assertTrue(json.contains("\n  \"schemaVersion\""));
        assertTrue(json.contains("\"kpok2-question-export-v1\""));
        assertTrue(json.contains("\"contentHash\" : \"asset-hash\""));
        assertFalse(json.contains("AQID"));
        assertEquals(1,
                fixture.objectMapper.readTree(target.toFile())
                        .path("summary")
                        .path("canonicalQuestions")
                        .asInt());
    }

    @Test
    void providesSortedFilterOptionsWithoutLazyPerOriginQueries() {
        Fixture fixture = fixture();
        QuestionOrigin englishOrigin = QuestionOrigin.builder()
                .id(31L)
                .question(fixture.question)
                .quizId(174L)
                .quizUrl("https://moodle.example/mod/quiz/view.php?id=174")
                .language("EN")
                .firstSeenAt(fixture.now)
                .lastSeenAt(fixture.now)
                .build();
        when(fixture.origins.findAllByOrderByQuizIdAscLanguageAscIdAsc())
                .thenReturn(List.of(fixture.origin, englishOrigin));
        when(fixture.revisions.findAllByOrderByQuestionIdAscIdAsc())
                .thenReturn(List.of(fixture.revision));

        var options = fixture.service.getOptions();

        assertEquals(List.of("en", "uk"), options.languages());
        assertEquals(List.of(173L, 174L), options.quizzes().stream()
                .map(option -> option.quizId())
                .toList());
        assertEquals(1L, options.quizzes().get(0).questionCount());
        assertEquals(List.of(QuestionType.SINGLE_CHOICE), options.questionTypes());
        verify(fixture.origins).findAllByOrderByQuizIdAscLanguageAscIdAsc();
        verify(fixture.revisions).findAllByOrderByQuestionIdAscIdAsc();
    }

    private Fixture fixture() {
        TestQuestionRepository questions = mock(TestQuestionRepository.class);
        QuestionRevisionRepository revisions = mock(QuestionRevisionRepository.class);
        AnswerKeyRepository keys = mock(AnswerKeyRepository.class);
        QuestionOriginRepository origins = mock(QuestionOriginRepository.class);
        QuestionObservationRepository observations =
                mock(QuestionObservationRepository.class);
        QuestionRevisionAssetRepository assets =
                mock(QuestionRevisionAssetRepository.class);
        CollectionAttemptRepository attempts = mock(CollectionAttemptRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        QuestionExportService service = new QuestionExportService(
                questions,
                revisions,
                keys,
                origins,
                observations,
                assets,
                attempts,
                new JsonConverter(objectMapper),
                objectMapper);
        return new Fixture(
                service,
                objectMapper,
                questions,
                revisions,
                keys,
                origins,
                observations,
                assets,
                attempts);
    }

    private static final class Fixture {
        private final QuestionExportService service;
        private final ObjectMapper objectMapper;
        private final TestQuestionRepository questions;
        private final QuestionRevisionRepository revisions;
        private final AnswerKeyRepository keys;
        private final QuestionOriginRepository origins;
        private final QuestionObservationRepository observations;
        private final QuestionRevisionAssetRepository assets;
        private final CollectionAttemptRepository attempts;
        private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);
        private final TestQuestion question;
        private final QuestionRevision revision;
        private final AnswerKey firstKey;
        private final AnswerKey secondKey;
        private final QuestionOrigin origin;
        private final QuestionObservation firstObservation;
        private final QuestionObservation secondObservation;
        private final QuestionAssetExportMetadata assetMetadata;
        private final CollectionAttempt attempt;

        private Fixture(
                QuestionExportService service,
                ObjectMapper objectMapper,
                TestQuestionRepository questions,
                QuestionRevisionRepository revisions,
                AnswerKeyRepository keys,
                QuestionOriginRepository origins,
                QuestionObservationRepository observations,
                QuestionRevisionAssetRepository assets,
                CollectionAttemptRepository attempts) {
            this.service = service;
            this.objectMapper = objectMapper;
            this.questions = questions;
            this.revisions = revisions;
            this.keys = keys;
            this.origins = origins;
            this.observations = observations;
            this.assets = assets;
            this.attempts = attempts;
            question = TestQuestion.builder()
                    .id(1L)
                    .questionHash("question-hash")
                    .questionText("Question")
                    .normalizedText("question")
                    .contextText("Case")
                    .normalizedContext("case")
                    .normalizationVersion(5)
                    .questionType(QuestionType.SINGLE_CHOICE)
                    .parsedAt(now)
                    .build();
            revision = QuestionRevision.builder()
                    .id(10L)
                    .question(question)
                    .revisionHash("revision-hash")
                    .contextText("Case")
                    .questionText("Question")
                    .contextHtml("<p>Case</p>")
                    .questionHtml("<p>Question <strong>HTML</strong></p>")
                    .possibleAnswersJson("[\"Alpha\",\"Beta\"]")
                    .normalizedAnswersJson("[\"alpha\",\"beta\"]")
                    .answerHtmlJson("[\"<p>Alpha</p>\",\"<p>Beta</p>\"]")
                    .questionType(QuestionType.SINGLE_CHOICE)
                    .firstSeenAt(now)
                    .lastSeenAt(now)
                    .build();
            firstKey = answerKey(20L, "first-key", "[\"Beta\"]", "[\"beta\"]");
            secondKey = answerKey(21L, "second-key", "[\"Alpha\"]", "[\"alpha\"]");
            origin = QuestionOrigin.builder()
                    .id(30L)
                    .question(question)
                    .quizId(173L)
                    .quizUrl("https://moodle.example/mod/quiz/view.php?id=173")
                    .language("uk")
                    .firstSeenAt(now)
                    .lastSeenAt(now)
                    .build();
            firstObservation = observation(31L, firstKey);
            secondObservation = observation(32L, secondKey);
            QuestionAsset asset = QuestionAsset.builder()
                    .id(40L)
                    .contentHash("asset-hash")
                    .mediaType("image/png")
                    .originalFileName("diagram.png")
                    .contentLength(3)
                    .content(new byte[]{1, 2, 3})
                    .createdAt(now)
                    .build();
            assetMetadata = new QuestionAssetExportMetadata(
                    41L,
                    revision.getId(),
                    asset.getId(),
                    asset.getContentHash(),
                    asset.getMediaType(),
                    asset.getOriginalFileName(),
                    asset.getContentLength(),
                    QuestionContentLocation.QUESTION,
                    -1,
                    0,
                    "https://moodle.example/diagram.png",
                    "Diagnostic diagram",
                    AssetFetchStatus.STORED,
                    null);
            attempt = CollectionAttempt.builder()
                    .id(50L)
                    .quizId(173L)
                    .sourceAttemptId("456")
                    .quizUrl(origin.getQuizUrl())
                    .reviewUrl("https://moodle.example/mod/quiz/review.php?attempt=456")
                    .firstCollectedAt(now)
                    .lastCollectedAt(now)
                    .collectionRuns(1)
                    .pageCount(1)
                    .successfulPageCount(1)
                    .discoveredQuestions(1)
                    .parsedQuestions(1)
                    .initialNewQuestions(1)
                    .latestNewQuestions(1)
                    .latestDuplicateQuestions(0)
                    .rejectedQuestions(0)
                    .complete(true)
                    .build();
        }

        private AnswerKey answerKey(
                Long id, String hash, String correct, String normalized) {
            return AnswerKey.builder()
                    .id(id)
                    .revision(revision)
                    .keyHash(hash)
                    .correctAnswersJson(correct)
                    .normalizedCorrectAnswersJson(normalized)
                    .firstSeenAt(now)
                    .lastSeenAt(now)
                    .build();
        }

        private QuestionObservation observation(Long id, AnswerKey key) {
            return QuestionObservation.builder()
                    .id(id)
                    .question(question)
                    .revision(revision)
                    .answerKey(key)
                    .quizId(173L)
                    .quizUrl(origin == null
                            ? "https://moodle.example/mod/quiz/view.php?id=173"
                            : origin.getQuizUrl())
                    .sourceAttemptId("456")
                    .language("uk")
                    .firstSeenAt(now)
                    .lastSeenAt(now)
                    .observationCount(1)
                    .build();
        }

        private void stubCompleteQuestion() {
            when(questions.findAllByOrderByIdAsc()).thenReturn(List.of(question));
            when(origins.findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                    List.of(1L))).thenReturn(List.of(origin));
            when(revisions.findAllByQuestionIdInOrderByQuestionIdAscIdAsc(
                    List.of(1L))).thenReturn(List.of(revision));
            when(keys.findAllByRevisionIdInOrderByRevisionIdAscIdAsc(
                    List.of(10L))).thenReturn(List.of(firstKey, secondKey));
            when(observations
                    .findAllByRevisionIdInOrderByRevisionIdAscAnswerKeyIdAscIdAsc(
                            List.of(10L)))
                    .thenReturn(List.of(firstObservation, secondObservation));
            when(assets
                    .findExportMetadataByRevisionIds(List.of(10L)))
                    .thenReturn(List.of(assetMetadata));
        }
    }
}
