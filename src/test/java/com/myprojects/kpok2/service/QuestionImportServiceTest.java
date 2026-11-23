package com.myprojects.kpok2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.service.mapper.TestQuestionMapperUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuestionImportServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();
    private final QuestionImportService service = new QuestionImportService(
            objectMapper);

    @Test
    void readsAndPreviewsAValidVersionedExport() throws Exception {
        Path source = tempDir.resolve("questions.json");
        objectMapper.writeValue(source.toFile(), validDocument());

        var inspection = service.read(source);

        assertEquals(QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                inspection.preview().schemaVersion());
        assertEquals(1, inspection.preview().contents().canonicalQuestions());
        assertEquals(1, inspection.preview().contents().revisions());
        assertEquals(1, inspection.preview().contents().answerKeys());
        assertEquals(1, inspection.preview().contents().origins());
        assertEquals(1, inspection.preview().contents().observations());
        assertEquals(1, inspection.preview().contents().attempts());
        assertFalse(inspection.preview().filteredExport());
        assertTrue(inspection.preview().warnings().stream()
                .anyMatch(message -> message.contains("IDs")));
        assertEquals(validDocument().questions(), inspection.document().questions());
    }

    @Test
    void rejectsAnUnsupportedSchemaBeforeAnyImportCanStart() {
        QuestionExportDocument valid = validDocument();
        QuestionExportDocument unsupported = new QuestionExportDocument(
                "kpok2-question-export-v99",
                valid.generatedAt(),
                valid.filters(),
                valid.summary(),
                valid.questions(),
                valid.attempts());

        QuestionImportValidationException error = assertThrows(
                QuestionImportValidationException.class,
                () -> service.inspect(unsupported));

        assertTrue(error.getErrors().stream()
                .anyMatch(message -> message.contains("Unsupported schemaVersion")));
    }

    @Test
    void rejectsTamperedSummaryAndDuplicateQuestionHashes() {
        QuestionExportDocument valid = validDocument();
        QuestionExportDocument tampered = new QuestionExportDocument(
                valid.schemaVersion(),
                valid.generatedAt(),
                valid.filters(),
                new QuestionExportDocument.ExportSummary(
                        999, 1, 1, 0, 1, 1, 0, 1),
                List.of(valid.questions().get(0), valid.questions().get(0)),
                valid.attempts());

        QuestionImportValidationException error = assertThrows(
                QuestionImportValidationException.class,
                () -> service.inspect(tampered));

        assertTrue(error.getErrors().stream()
                .anyMatch(message -> message.contains("duplicated")));
        assertTrue(error.getErrors().stream()
                .anyMatch(message -> message.contains("summary")));
    }

    @Test
    void rejectsWellFormedButForgedIdentityHashes() {
        QuestionExportDocument valid = validDocument();
        var source = valid.questions().get(0);
        var forged = new QuestionExportDocument.ExportQuestion(
                source.id(),
                "f".repeat(32),
                source.questionText(),
                source.normalizedText(),
                source.contextText(),
                source.normalizedContext(),
                source.normalizationVersion(),
                source.questionType(),
                source.parsedAt(),
                source.origins(),
                source.revisions());
        QuestionExportDocument document = new QuestionExportDocument(
                valid.schemaVersion(),
                valid.generatedAt(),
                valid.filters(),
                valid.summary(),
                List.of(forged),
                valid.attempts());

        QuestionImportValidationException error = assertThrows(
                QuestionImportValidationException.class,
                () -> service.inspect(document));

        assertTrue(error.getErrors().stream()
                .anyMatch(message -> message.contains("questionHash does not match")));
    }

    @Test
    void rejectsForgedRevisionAndAnswerKeyHashes() {
        QuestionExportDocument valid = validDocument();
        var sourceQuestion = valid.questions().get(0);
        var sourceRevision = sourceQuestion.revisions().get(0);
        var sourceKey = sourceRevision.answerKeys().get(0);
        var forgedKey = new QuestionExportDocument.ExportAnswerKey(
                sourceKey.id(),
                "d".repeat(32),
                sourceKey.correctAnswers(),
                sourceKey.normalizedCorrectAnswers(),
                sourceKey.firstSeenAt(),
                sourceKey.lastSeenAt(),
                sourceKey.observations());
        var forgedRevision = new QuestionExportDocument.ExportRevision(
                sourceRevision.id(),
                "e".repeat(32),
                sourceRevision.contextText(),
                sourceRevision.questionText(),
                sourceRevision.contextHtml(),
                sourceRevision.questionHtml(),
                sourceRevision.answers(),
                sourceRevision.normalizedAnswers(),
                sourceRevision.answerHtml(),
                sourceRevision.questionType(),
                sourceRevision.firstSeenAt(),
                sourceRevision.lastSeenAt(),
                sourceRevision.answerKeyConflict(),
                List.of(forgedKey),
                sourceRevision.assets());
        var question = new QuestionExportDocument.ExportQuestion(
                sourceQuestion.id(),
                sourceQuestion.questionHash(),
                sourceQuestion.questionText(),
                sourceQuestion.normalizedText(),
                sourceQuestion.contextText(),
                sourceQuestion.normalizedContext(),
                sourceQuestion.normalizationVersion(),
                sourceQuestion.questionType(),
                sourceQuestion.parsedAt(),
                sourceQuestion.origins(),
                List.of(forgedRevision));
        QuestionExportDocument document = new QuestionExportDocument(
                valid.schemaVersion(),
                valid.generatedAt(),
                valid.filters(),
                valid.summary(),
                List.of(question),
                valid.attempts());

        QuestionImportValidationException error = assertThrows(
                QuestionImportValidationException.class,
                () -> service.inspect(document));

        assertTrue(error.getErrors().stream()
                .anyMatch(message -> message.contains("revisionHash does not match")));
        assertTrue(error.getErrors().stream()
                .anyMatch(message -> message.contains("keyHash does not match")));
    }

    @Test
    void rejectsUppercaseHashThatWouldBypassCaseSensitiveDeduplication() {
        QuestionExportDocument valid = validDocument();
        var source = valid.questions().get(0);
        var uppercase = new QuestionExportDocument.ExportQuestion(
                source.id(),
                source.questionHash().toUpperCase(java.util.Locale.ROOT),
                source.questionText(),
                source.normalizedText(),
                source.contextText(),
                source.normalizedContext(),
                source.normalizationVersion(),
                source.questionType(),
                source.parsedAt(),
                source.origins(),
                source.revisions());
        QuestionExportDocument document = new QuestionExportDocument(
                valid.schemaVersion(),
                valid.generatedAt(),
                valid.filters(),
                valid.summary(),
                List.of(uppercase),
                valid.attempts());

        assertThrows(QuestionImportValidationException.class,
                () -> service.inspect(document));
    }

    @Test
    void rejectsMalformedAndEmptyFiles() throws Exception {
        Path malformed = tempDir.resolve("malformed.json");
        Files.writeString(malformed, "{not-json");
        Path empty = tempDir.resolve("empty.json");
        Files.createFile(empty);

        assertThrows(
                QuestionImportValidationException.class,
                () -> service.read(malformed));
        assertThrows(
                QuestionImportValidationException.class,
                () -> service.read(empty));
    }

    public static QuestionExportDocument validDocument() {
        LocalDateTime seenAt = LocalDateTime.of(2026, 11, 17, 12, 0);
        String questionHash = TestQuestionMapperUtils.generateCanonicalHash(
                "case", "question");
        String revisionHash = TestQuestionMapperUtils.generateRevisionHash(
                questionHash,
                List.of("alpha", "beta"),
                QuestionType.SINGLE_CHOICE);
        String keyHash = TestQuestionMapperUtils.generateAnswerKeyHash(
                List.of("beta"));
        var observation = new QuestionExportDocument.ExportObservation(
                40L,
                173L,
                "https://test.example/mod/quiz/view.php?id=173",
                "456",
                "uk",
                seenAt,
                seenAt,
                1);
        var key = new QuestionExportDocument.ExportAnswerKey(
                30L,
                keyHash,
                List.of("Beta"),
                List.of("beta"),
                seenAt,
                seenAt,
                List.of(observation));
        var revision = new QuestionExportDocument.ExportRevision(
                20L,
                revisionHash,
                "Case",
                "Question",
                "<p>Case</p>",
                "<p>Question</p>",
                List.of("Alpha", "Beta"),
                List.of("alpha", "beta"),
                List.of("<p>Alpha</p>", "<p>Beta</p>"),
                QuestionType.SINGLE_CHOICE,
                seenAt,
                seenAt,
                false,
                List.of(key),
                List.of());
        var origin = new QuestionExportDocument.ExportOrigin(
                10L,
                173L,
                "https://test.example/mod/quiz/view.php?id=173",
                "uk",
                seenAt,
                seenAt);
        var question = new QuestionExportDocument.ExportQuestion(
                1L,
                questionHash,
                "Question",
                "question",
                "Case",
                "case",
                5,
                QuestionType.SINGLE_CHOICE,
                seenAt,
                List.of(origin),
                List.of(revision));
        var attempt = new QuestionExportDocument.ExportAttempt(
                50L,
                173L,
                "456",
                "https://test.example/mod/quiz/view.php?id=173",
                "https://test.example/mod/quiz/review.php?attempt=456",
                seenAt,
                seenAt,
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                0,
                0,
                true,
                null);
        return new QuestionExportDocument(
                QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-11-17T10:00:00Z"),
                new QuestionExportDocument.ExportFilter(
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        true,
                        ""),
                new QuestionExportDocument.ExportSummary(
                        1, 1, 1, 0, 1, 1, 0, 1),
                List.of(question),
                List.of(attempt));
    }
}
