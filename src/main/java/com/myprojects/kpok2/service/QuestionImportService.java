package com.myprojects.kpok2.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.importing.QuestionImportPreview;
import com.myprojects.kpok2.service.parser.TestNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads and validates versioned question exports without changing the database. */
@Service
@RequiredArgsConstructor
public class QuestionImportService {

    public static final long MAX_IMPORT_BYTES = 100L * 1024L * 1024L;
    private static final int MAX_VALIDATION_ERRORS = 100;

    private final ObjectMapper objectMapper;

    public Inspection read(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Import source is required");
        }
        Path absoluteSource = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluteSource)) {
            throw new IOException("Import file does not exist: " + absoluteSource);
        }
        long size = Files.size(absoluteSource);
        if (size == 0) {
            throw new QuestionImportValidationException(
                    List.of("Import file is empty"));
        }
        if (size > MAX_IMPORT_BYTES) {
            throw new QuestionImportValidationException(List.of(
                    "Import file exceeds the 100 MiB safety limit"));
        }

        QuestionExportDocument document;
        try {
            document = objectMapper.readerFor(QuestionExportDocument.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(absoluteSource.toFile());
        } catch (JsonProcessingException e) {
            throw new QuestionImportValidationException(
                    List.of("File is not a valid question export JSON document"), e);
        }
        return new Inspection(document, inspect(document));
    }

    public QuestionImportPreview inspect(QuestionExportDocument document) {
        ValidationErrors errors = new ValidationErrors();
        if (document == null) {
            throw new QuestionImportValidationException(
                    List.of("Import document is required"));
        }
        if (!QuestionExportDocument.CURRENT_SCHEMA_VERSION.equals(
                document.schemaVersion())) {
            errors.add("Unsupported schemaVersion: " + printable(
                    document.schemaVersion()));
        }
        if (document.generatedAt() == null) {
            errors.add("generatedAt is required");
        }
        if (document.summary() == null) {
            errors.add("summary is required");
        }

        Counters counters = new Counters();
        Set<String> questionHashes = new HashSet<>();
        for (int questionIndex = 0;
                questionIndex < document.questions().size();
                questionIndex++) {
            var question = document.questions().get(questionIndex);
            String questionPath = "questions[" + questionIndex + "]";
            counters.questions++;
            requireHash(question.questionHash(), questionPath + ".questionHash", errors);
            if (hasText(question.questionHash())
                    && !questionHashes.add(question.questionHash())) {
                errors.add(questionPath + ".questionHash is duplicated");
            }
            requireText(question.questionText(), questionPath + ".questionText", errors);
            requireText(question.normalizedText(),
                    questionPath + ".normalizedText", errors);
            if (question.questionType() == null) {
                errors.add(questionPath + ".questionType is required");
            }
            if (question.normalizationVersion() <= 0) {
                errors.add(questionPath + ".normalizationVersion must be positive");
            }
            if (question.parsedAt() == null) {
                errors.add(questionPath + ".parsedAt is required");
            }
            validateOrigins(question.origins(), questionPath, counters, errors);
            validateRevisions(question.revisions(), questionPath, counters, errors);
        }
        validateAttempts(document.attempts(), counters, errors);

        QuestionExportDocument.ExportSummary contents = counters.toSummary();
        if (document.summary() != null && !document.summary().equals(contents)) {
            errors.add("summary does not match the document contents");
        }
        errors.throwIfAny();

        List<String> warnings = new ArrayList<>();
        if (contents.canonicalQuestions() == 0) {
            warnings.add("The export contains no questions");
        }
        if (isFiltered(document.filters())) {
            warnings.add("The source is a filtered export, not a full backup");
        }
        if (contents.assets() > 0) {
            warnings.add("Image metadata is present, but JSON does not contain image files");
        }
        if (document.questions().stream().anyMatch(question ->
                question.normalizationVersion() > TestNormalizer.CURRENT_VERSION)) {
            warnings.add("The export uses a newer text-normalization version");
        }
        warnings.add("Database IDs from the source will not be reused");
        return new QuestionImportPreview(
                document.schemaVersion(),
                document.generatedAt(),
                contents,
                isFiltered(document.filters()),
                warnings);
    }

    private void validateOrigins(
            List<QuestionExportDocument.ExportOrigin> origins,
            String questionPath,
            Counters counters,
            ValidationErrors errors) {
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < origins.size(); index++) {
            var origin = origins.get(index);
            String path = questionPath + ".origins[" + index + "]";
            counters.origins++;
            requirePositive(origin.quizId(), path + ".quizId", errors);
            requireText(origin.quizUrl(), path + ".quizUrl", errors);
            requireText(origin.language(), path + ".language", errors);
            validateTimeRange(
                    origin.firstSeenAt(), origin.lastSeenAt(), path, errors);
            String identity = origin.quizId() + "\u0000" + normalized(
                    origin.language());
            if (!identities.add(identity)) {
                errors.add(path + " duplicates a quiz/language origin");
            }
        }
    }

    private void validateRevisions(
            List<QuestionExportDocument.ExportRevision> revisions,
            String questionPath,
            Counters counters,
            ValidationErrors errors) {
        if (revisions.isEmpty()) {
            errors.add(questionPath + ".revisions must not be empty");
            return;
        }
        Set<String> revisionHashes = new HashSet<>();
        for (int revisionIndex = 0;
                revisionIndex < revisions.size();
                revisionIndex++) {
            var revision = revisions.get(revisionIndex);
            String path = questionPath + ".revisions[" + revisionIndex + "]";
            counters.revisions++;
            requireHash(revision.revisionHash(), path + ".revisionHash", errors);
            if (hasText(revision.revisionHash())
                    && !revisionHashes.add(revision.revisionHash())) {
                errors.add(path + ".revisionHash is duplicated within the question");
            }
            requireText(revision.questionText(), path + ".questionText", errors);
            if (revision.questionType() == null) {
                errors.add(path + ".questionType is required");
            }
            if (revision.answers().isEmpty()) {
                errors.add(path + ".answers must not be empty");
            }
            if (revision.answers().size() != revision.normalizedAnswers().size()) {
                errors.add(path + ".normalizedAnswers must match answers in size");
            }
            if (!revision.answerHtml().isEmpty()
                    && revision.answerHtml().size() != revision.answers().size()) {
                errors.add(path + ".answerHtml must be empty or match answers in size");
            }
            validateTimeRange(
                    revision.firstSeenAt(), revision.lastSeenAt(), path, errors);
            validateAnswerKeys(revision, path, counters, errors);
            counters.assets += revision.assets().size();
        }
    }

    private void validateAnswerKeys(
            QuestionExportDocument.ExportRevision revision,
            String revisionPath,
            Counters counters,
            ValidationErrors errors) {
        if (revision.answerKeys().isEmpty()) {
            errors.add(revisionPath + ".answerKeys must not be empty");
            return;
        }
        boolean conflict = revision.answerKeys().size() > 1;
        if (revision.answerKeyConflict() != conflict) {
            errors.add(revisionPath
                    + ".answerKeyConflict does not match the number of answer keys");
        }
        if (conflict) {
            counters.conflicts++;
        }
        Set<String> keyHashes = new HashSet<>();
        for (int keyIndex = 0;
                keyIndex < revision.answerKeys().size();
                keyIndex++) {
            var key = revision.answerKeys().get(keyIndex);
            String path = revisionPath + ".answerKeys[" + keyIndex + "]";
            counters.answerKeys++;
            requireHash(key.keyHash(), path + ".keyHash", errors);
            if (hasText(key.keyHash()) && !keyHashes.add(key.keyHash())) {
                errors.add(path + ".keyHash is duplicated within the revision");
            }
            if (key.correctAnswers().isEmpty()) {
                errors.add(path + ".correctAnswers must not be empty");
            }
            if (key.correctAnswers().size()
                    != key.normalizedCorrectAnswers().size()) {
                errors.add(path
                        + ".normalizedCorrectAnswers must match correctAnswers in size");
            }
            validateTimeRange(key.firstSeenAt(), key.lastSeenAt(), path, errors);
            validateObservations(key.observations(), path, counters, errors);
        }
    }

    private void validateObservations(
            List<QuestionExportDocument.ExportObservation> observations,
            String keyPath,
            Counters counters,
            ValidationErrors errors) {
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < observations.size(); index++) {
            var observation = observations.get(index);
            String path = keyPath + ".observations[" + index + "]";
            counters.observations++;
            requirePositive(observation.quizId(), path + ".quizId", errors);
            requireText(observation.quizUrl(), path + ".quizUrl", errors);
            requireText(observation.sourceAttemptId(),
                    path + ".sourceAttemptId", errors);
            requireText(observation.language(), path + ".language", errors);
            if (observation.observationCount() <= 0) {
                errors.add(path + ".observationCount must be positive");
            }
            validateTimeRange(
                    observation.firstSeenAt(), observation.lastSeenAt(), path, errors);
            String identity = observation.quizId()
                    + "\u0000" + observation.sourceAttemptId()
                    + "\u0000" + normalized(observation.language());
            if (!identities.add(identity)) {
                errors.add(path + " duplicates an observation");
            }
        }
    }

    private void validateAttempts(
            List<QuestionExportDocument.ExportAttempt> attempts,
            Counters counters,
            ValidationErrors errors) {
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < attempts.size(); index++) {
            var attempt = attempts.get(index);
            String path = "attempts[" + index + "]";
            counters.attempts++;
            requirePositive(attempt.quizId(), path + ".quizId", errors);
            requireText(attempt.sourceAttemptId(), path + ".sourceAttemptId", errors);
            requireText(attempt.quizUrl(), path + ".quizUrl", errors);
            requireText(attempt.reviewUrl(), path + ".reviewUrl", errors);
            validateTimeRange(
                    attempt.firstCollectedAt(),
                    attempt.lastCollectedAt(),
                    path,
                    errors);
            if (attempt.collectionRuns() <= 0) {
                errors.add(path + ".collectionRuns must be positive");
            }
            if (attempt.pageCount() < 0
                    || attempt.successfulPageCount() < 0
                    || attempt.discoveredQuestions() < 0
                    || attempt.parsedQuestions() < 0
                    || attempt.initialNewQuestions() < 0
                    || attempt.latestNewQuestions() < 0
                    || attempt.latestDuplicateQuestions() < 0
                    || attempt.rejectedQuestions() < 0) {
                errors.add(path + " contains a negative count");
            }
            if (attempt.successfulPageCount() > attempt.pageCount()) {
                errors.add(path + ".successfulPageCount exceeds pageCount");
            }
            String identity = attempt.quizId()
                    + "\u0000" + attempt.sourceAttemptId();
            if (!identities.add(identity)) {
                errors.add(path + " duplicates a collection attempt");
            }
        }
    }

    private void validateTimeRange(
            LocalDateTime first,
            LocalDateTime last,
            String path,
            ValidationErrors errors) {
        if (first == null) {
            errors.add(path + ".firstSeen/CollectedAt is required");
        }
        if (last == null) {
            errors.add(path + ".lastSeen/CollectedAt is required");
        }
        if (first != null && last != null && first.isAfter(last)) {
            errors.add(path + " has an inverted time range");
        }
    }

    private void requireHash(
            String value, String path, ValidationErrors errors) {
        if (!hasText(value)
                || !value.matches("(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{64})")) {
            errors.add(path + " must be a supported 32- or 64-character hash");
        }
    }

    private void requireText(
            String value, String path, ValidationErrors errors) {
        if (!hasText(value)) {
            errors.add(path + " is required");
        }
    }

    private void requirePositive(
            Long value, String path, ValidationErrors errors) {
        if (value == null || value <= 0) {
            errors.add(path + " must be positive");
        }
    }

    private boolean isFiltered(QuestionExportDocument.ExportFilter filter) {
        return filter != null && (
                filter.quizIds() != null && !filter.quizIds().isEmpty()
                        || filter.languages() != null && !filter.languages().isEmpty()
                        || filter.questionTypes() != null
                        && !filter.questionTypes().isEmpty()
                        || filter.conflictsOnly()
                        || filter.query() != null && !filter.query().isBlank()
                        || !filter.includeAttempts());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String printable(String value) {
        return value == null ? "<missing>" : value;
    }

    public record Inspection(
            QuestionExportDocument document,
            QuestionImportPreview preview) {
    }

    private static final class Counters {
        private int questions;
        private int revisions;
        private int answerKeys;
        private int conflicts;
        private int origins;
        private int observations;
        private int assets;
        private int attempts;

        private QuestionExportDocument.ExportSummary toSummary() {
            return new QuestionExportDocument.ExportSummary(
                    questions,
                    revisions,
                    answerKeys,
                    conflicts,
                    origins,
                    observations,
                    assets,
                    attempts);
        }
    }

    private static final class ValidationErrors {
        private final List<String> values = new ArrayList<>();
        private boolean truncated;

        private void add(String error) {
            if (values.size() < MAX_VALIDATION_ERRORS) {
                values.add(error);
            } else {
                truncated = true;
            }
        }

        private void throwIfAny() {
            if (values.isEmpty()) {
                return;
            }
            if (truncated) {
                values.add("Additional validation errors were omitted");
            }
            throw new QuestionImportValidationException(values);
        }
    }
}
