package com.myprojects.kpok2.model.dto.export;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.QuestionType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Versioned, deterministic and import-friendly representation of collected questions. */
public record QuestionExportDocument(
        String schemaVersion,
        Instant generatedAt,
        ExportFilter filters,
        ExportSummary summary,
        List<ExportQuestion> questions,
        List<ExportAttempt> attempts) {

    public static final String CURRENT_SCHEMA_VERSION = "kpok2-question-export-v1";

    public QuestionExportDocument {
        questions = questions == null ? List.of() : List.copyOf(questions);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }

    public record ExportFilter(
            List<Long> quizIds,
            List<String> languages,
            List<QuestionType> questionTypes,
            boolean conflictsOnly,
            boolean includeAttempts,
            String query) {
    }

    public record ExportSummary(
            int canonicalQuestions,
            int revisions,
            int answerKeys,
            int conflicts,
            int origins,
            int observations,
            int assets,
            int attempts) {
    }

    public record ExportQuestion(
            Long id,
            String questionHash,
            String questionText,
            String normalizedText,
            String contextText,
            String normalizedContext,
            int normalizationVersion,
            QuestionType questionType,
            LocalDateTime parsedAt,
            List<ExportOrigin> origins,
            List<ExportRevision> revisions) {

        public ExportQuestion {
            origins = origins == null ? List.of() : List.copyOf(origins);
            revisions = revisions == null ? List.of() : List.copyOf(revisions);
        }
    }

    public record ExportOrigin(
            Long id,
            Long quizId,
            String quizUrl,
            String language,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt) {
    }

    public record ExportRevision(
            Long id,
            String revisionHash,
            String contextText,
            String questionText,
            String contextHtml,
            String questionHtml,
            List<String> answers,
            List<String> normalizedAnswers,
            List<String> answerHtml,
            QuestionType questionType,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            boolean answerKeyConflict,
            List<ExportAnswerKey> answerKeys,
            List<ExportAsset> assets) {

        public ExportRevision {
            answers = answers == null ? List.of() : List.copyOf(answers);
            normalizedAnswers = normalizedAnswers == null
                    ? List.of()
                    : List.copyOf(normalizedAnswers);
            answerHtml = answerHtml == null ? List.of() : List.copyOf(answerHtml);
            answerKeys = answerKeys == null ? List.of() : List.copyOf(answerKeys);
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }

    public record ExportAnswerKey(
            Long id,
            String keyHash,
            List<String> correctAnswers,
            List<String> normalizedCorrectAnswers,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            List<ExportObservation> observations) {

        public ExportAnswerKey {
            correctAnswers = correctAnswers == null
                    ? List.of()
                    : List.copyOf(correctAnswers);
            normalizedCorrectAnswers = normalizedCorrectAnswers == null
                    ? List.of()
                    : List.copyOf(normalizedCorrectAnswers);
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }

    public record ExportObservation(
            Long id,
            Long quizId,
            String quizUrl,
            String sourceAttemptId,
            String language,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            int observationCount) {
    }

    public record ExportAsset(
            Long referenceId,
            Long assetId,
            String contentHash,
            String mediaType,
            String originalFileName,
            long contentLength,
            QuestionContentLocation contentLocation,
            int answerIndex,
            int sequenceIndex,
            String sourceUrl,
            String altText,
            AssetFetchStatus fetchStatus,
            String failureReason,
            String contentUrl) {
    }

    public record ExportAttempt(
            Long id,
            Long quizId,
            String sourceAttemptId,
            String quizUrl,
            String reviewUrl,
            LocalDateTime firstCollectedAt,
            LocalDateTime lastCollectedAt,
            int collectionRuns,
            int pageCount,
            int successfulPageCount,
            int discoveredQuestions,
            int parsedQuestions,
            int initialNewQuestions,
            int latestNewQuestions,
            int latestDuplicateQuestions,
            int rejectedQuestions,
            boolean complete,
            String lastError) {
    }
}
