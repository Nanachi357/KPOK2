package com.myprojects.kpok2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.CollectionAttempt;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.export.QuestionAssetExportMetadata;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.model.dto.export.QuestionExportOptionsDto;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.CollectionAttemptRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import com.myprojects.kpok2.repository.QuestionRevisionAssetRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.util.JsonConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds and writes the versioned portable JSON question export. */
@Service
@RequiredArgsConstructor
public class QuestionExportService {

    private final TestQuestionRepository questionRepository;
    private final QuestionRevisionRepository revisionRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final QuestionOriginRepository originRepository;
    private final QuestionObservationRepository observationRepository;
    private final QuestionRevisionAssetRepository revisionAssetRepository;
    private final CollectionAttemptRepository attemptRepository;
    private final JsonConverter jsonConverter;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public QuestionExportDocument buildExport(QuestionExportFilter requestedFilter) {
        QuestionExportFilter filter = requestedFilter == null
                ? QuestionExportFilter.all()
                : requestedFilter;
        ExportData data = loadData();
        List<QuestionExportDocument.ExportQuestion> exportedQuestions = new ArrayList<>();
        Set<Long> includedQuizIds = new LinkedHashSet<>();

        for (TestQuestion question : data.questions()) {
            List<QuestionOrigin> allOrigins = data.originsByQuestion()
                    .getOrDefault(question.getId(), List.of());
            List<QuestionOrigin> matchedOrigins = allOrigins.stream()
                    .filter(origin -> matchesScope(
                            origin.getQuizId(), origin.getLanguage(), filter))
                    .toList();
            List<QuestionRevision> revisions = data.revisionsByQuestion()
                    .getOrDefault(question.getId(), List.of());
            List<QuestionExportDocument.ExportRevision> exportedRevisions =
                    exportRevisions(
                            question,
                            revisions,
                            matchedOrigins,
                            data,
                            filter,
                            includedQuizIds);

            boolean scopeMatched = !filter.hasScopeFilter()
                    || !matchedOrigins.isEmpty()
                    || exportedRevisions.stream()
                    .flatMap(revision -> revision.answerKeys().stream())
                    .flatMap(key -> key.observations().stream())
                    .findAny()
                    .isPresent();
            if (!scopeMatched || exportedRevisions.isEmpty()) {
                continue;
            }

            List<QuestionExportDocument.ExportOrigin> originsToExport =
                    (filter.hasScopeFilter() ? matchedOrigins : allOrigins).stream()
                            .map(this::toOrigin)
                            .toList();
            originsToExport.forEach(origin -> includedQuizIds.add(origin.quizId()));
            exportedQuestions.add(new QuestionExportDocument.ExportQuestion(
                    question.getId(),
                    question.getQuestionHash(),
                    question.getQuestionText(),
                    question.getNormalizedText(),
                    question.getContextText(),
                    question.getNormalizedContext(),
                    question.getNormalizationVersion() == null
                            ? 0
                            : question.getNormalizationVersion(),
                    question.getQuestionType(),
                    question.getParsedAt(),
                    originsToExport,
                    exportedRevisions));
        }

        List<QuestionExportDocument.ExportAttempt> attempts = exportAttempts(
                filter, includedQuizIds);
        QuestionExportDocument.ExportSummary summary = summarize(
                exportedQuestions, attempts);
        return new QuestionExportDocument(
                QuestionExportDocument.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                filterSnapshot(filter),
                summary,
                exportedQuestions,
                attempts);
    }

    @Transactional(readOnly = true)
    public QuestionExportOptionsDto getOptions() {
        List<QuestionOrigin> origins = originRepository
                .findAllByOrderByQuizIdAscLanguageAscIdAsc();
        Map<Long, List<QuestionOrigin>> byQuiz = origins.stream()
                .collect(Collectors.groupingBy(
                        QuestionOrigin::getQuizId,
                        TreeMap::new,
                        Collectors.toList()));
        List<QuestionExportOptionsDto.QuizOption> quizzes = byQuiz.entrySet().stream()
                .map(entry -> new QuestionExportOptionsDto.QuizOption(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(QuestionOrigin::getQuizUrl)
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst()
                                .orElse(""),
                        entry.getValue().stream()
                                .map(QuestionOrigin::getLanguage)
                                .filter(value -> value != null && !value.isBlank())
                                .map(value -> value.toLowerCase(Locale.ROOT))
                                .distinct()
                                .sorted()
                                .toList(),
                        entry.getValue().stream()
                                .map(origin -> origin.getQuestion().getId())
                                .distinct()
                                .count()))
                .toList();
        List<String> languages = origins.stream()
                .map(QuestionOrigin::getLanguage)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        EnumSet<QuestionType> presentTypes = revisionRepository
                .findAllByOrderByQuestionIdAscIdAsc().stream()
                .map(QuestionRevision::getQuestionType)
                .filter(type -> type != null)
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(QuestionType.class)));
        return new QuestionExportOptionsDto(
                quizzes,
                languages,
                presentTypes.stream()
                        .sorted(Comparator.comparing(QuestionType::name))
                        .toList());
    }

    @Transactional(readOnly = true)
    public QuestionExportDocument.ExportSummary writeJson(
            Path target, QuestionExportFilter filter) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("Export target is required");
        }
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Export directory does not exist: " + parent);
        }

        QuestionExportDocument document = buildExport(filter);
        Path temporary = Files.createTempFile(
                parent,
                absoluteTarget.getFileName().toString() + ".",
                ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporary.toFile(), document);
            try {
                Files.move(
                        temporary,
                        absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temporary,
                        absoluteTarget,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return document.summary();
    }

    private ExportData loadData() {
        List<TestQuestion> questions = questionRepository.findAllByOrderByIdAsc();
        if (questions.isEmpty()) {
            return ExportData.empty();
        }
        List<Long> questionIds = questions.stream().map(TestQuestion::getId).toList();
        List<QuestionOrigin> origins = originRepository
                .findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                        questionIds);
        List<QuestionRevision> revisions = revisionRepository
                .findAllByQuestionIdInOrderByQuestionIdAscIdAsc(questionIds);
        List<Long> revisionIds = revisions.stream().map(QuestionRevision::getId).toList();
        List<AnswerKey> keys = revisionIds.isEmpty()
                ? List.of()
                : answerKeyRepository
                .findAllByRevisionIdInOrderByRevisionIdAscIdAsc(revisionIds);
        List<QuestionObservation> observations = revisionIds.isEmpty()
                ? List.of()
                : observationRepository
                .findAllByRevisionIdInOrderByRevisionIdAscAnswerKeyIdAscIdAsc(
                        revisionIds);
        List<QuestionAssetExportMetadata> assets = revisionIds.isEmpty()
                ? List.of()
                : revisionAssetRepository
                .findExportMetadataByRevisionIds(revisionIds);
        return new ExportData(
                questions,
                groupBy(origins, origin -> origin.getQuestion().getId()),
                groupBy(revisions, revision -> revision.getQuestion().getId()),
                groupBy(keys, key -> key.getRevision().getId()),
                groupBy(observations, observation -> observation.getAnswerKey().getId()),
                groupBy(assets, QuestionAssetExportMetadata::revisionId));
    }

    private List<QuestionExportDocument.ExportRevision> exportRevisions(
            TestQuestion question,
            List<QuestionRevision> revisions,
            List<QuestionOrigin> matchedOrigins,
            ExportData data,
            QuestionExportFilter filter,
            Set<Long> includedQuizIds) {
        List<QuestionExportDocument.ExportRevision> result = new ArrayList<>();
        for (QuestionRevision revision : revisions) {
            if (!filter.questionTypes().isEmpty()
                    && !filter.questionTypes().contains(revision.getQuestionType())) {
                continue;
            }
            List<QuestionExportDocument.ExportAnswerKey> exportedKeys = new ArrayList<>();
            for (AnswerKey key : data.keysByRevision()
                    .getOrDefault(revision.getId(), List.of())) {
                List<QuestionObservation> matchedObservations = data.observationsByKey()
                        .getOrDefault(key.getId(), List.of())
                        .stream()
                        .filter(observation -> matchesScope(
                                observation.getQuizId(),
                                observation.getLanguage(),
                                filter))
                        .toList();
                if (filter.hasScopeFilter()
                        && matchedObservations.isEmpty()
                        && matchedOrigins.isEmpty()) {
                    continue;
                }
                exportedKeys.add(toAnswerKey(
                        key,
                        filter.hasScopeFilter()
                                ? matchedObservations
                                : data.observationsByKey()
                                .getOrDefault(key.getId(), List.of())));
            }
            boolean conflict = exportedKeys.size() > 1;
            if (exportedKeys.isEmpty() || (filter.conflictsOnly() && !conflict)) {
                continue;
            }
            List<QuestionAssetExportMetadata> assets = data.assetsByRevision()
                    .getOrDefault(revision.getId(), List.of());
            if (!matchesQuery(question, revision, exportedKeys, assets, filter.query())) {
                continue;
            }
            exportedKeys.stream()
                    .flatMap(key -> key.observations().stream())
                    .forEach(observation -> includedQuizIds.add(observation.quizId()));
            result.add(new QuestionExportDocument.ExportRevision(
                    revision.getId(),
                    revision.getRevisionHash(),
                    revision.getContextText(),
                    revision.getQuestionText(),
                    revision.getContextHtml(),
                    revision.getQuestionHtml(),
                    jsonConverter.getAnswersFromJson(revision.getPossibleAnswersJson()),
                    jsonConverter.getAnswersFromJson(revision.getNormalizedAnswersJson()),
                    jsonConverter.getAnswersFromJson(revision.getAnswerHtmlJson()),
                    revision.getQuestionType(),
                    revision.getFirstSeenAt(),
                    revision.getLastSeenAt(),
                    conflict,
                    exportedKeys,
                    assets.stream().map(this::toAsset).toList()));
        }
        return result;
    }

    private QuestionExportDocument.ExportAnswerKey toAnswerKey(
            AnswerKey key, List<QuestionObservation> observations) {
        return new QuestionExportDocument.ExportAnswerKey(
                key.getId(),
                key.getKeyHash(),
                jsonConverter.getAnswersFromJson(key.getCorrectAnswersJson()),
                jsonConverter.getAnswersFromJson(
                        key.getNormalizedCorrectAnswersJson()),
                key.getFirstSeenAt(),
                key.getLastSeenAt(),
                observations.stream().map(this::toObservation).toList());
    }

    private QuestionExportDocument.ExportOrigin toOrigin(QuestionOrigin origin) {
        return new QuestionExportDocument.ExportOrigin(
                origin.getId(),
                origin.getQuizId(),
                origin.getQuizUrl(),
                origin.getLanguage(),
                origin.getFirstSeenAt(),
                origin.getLastSeenAt());
    }

    private QuestionExportDocument.ExportObservation toObservation(
            QuestionObservation observation) {
        return new QuestionExportDocument.ExportObservation(
                observation.getId(),
                observation.getQuizId(),
                observation.getQuizUrl(),
                observation.getSourceAttemptId(),
                observation.getLanguage(),
                observation.getFirstSeenAt(),
                observation.getLastSeenAt(),
                observation.getObservationCount());
    }

    private QuestionExportDocument.ExportAsset toAsset(
            QuestionAssetExportMetadata metadata) {
        return new QuestionExportDocument.ExportAsset(
                metadata.referenceId(),
                metadata.assetId(),
                metadata.contentHash(),
                metadata.mediaType(),
                metadata.originalFileName(),
                metadata.contentLength() == null ? 0 : metadata.contentLength(),
                metadata.contentLocation(),
                metadata.answerIndex(),
                metadata.sequenceIndex(),
                metadata.sourceUrl(),
                metadata.altText(),
                metadata.fetchStatus(),
                metadata.failureReason(),
                metadata.assetId() == null
                        ? null
                        : "/api/assets/" + metadata.assetId());
    }

    private List<QuestionExportDocument.ExportAttempt> exportAttempts(
            QuestionExportFilter filter, Set<Long> includedQuizIds) {
        if (!filter.includeAttempts()) {
            return List.of();
        }
        Collection<Long> selectedQuizIds;
        if (!filter.quizIds().isEmpty() && !filter.hasNonQuizFilter()) {
            selectedQuizIds = filter.quizIds();
        } else if (filter.hasQuestionFilter()) {
            selectedQuizIds = includedQuizIds;
        } else {
            selectedQuizIds = List.of();
        }
        List<CollectionAttempt> attempts = selectedQuizIds.isEmpty()
                ? (filter.hasQuestionFilter()
                ? List.of()
                : attemptRepository.findAllByOrderByQuizIdAscIdAsc())
                : attemptRepository.findAllByQuizIdInOrderByQuizIdAscIdAsc(
                selectedQuizIds);
        return attempts.stream().map(this::toAttempt).toList();
    }

    private QuestionExportDocument.ExportAttempt toAttempt(CollectionAttempt attempt) {
        return new QuestionExportDocument.ExportAttempt(
                attempt.getId(),
                attempt.getQuizId(),
                attempt.getSourceAttemptId(),
                attempt.getQuizUrl(),
                attempt.getReviewUrl(),
                attempt.getFirstCollectedAt(),
                attempt.getLastCollectedAt(),
                attempt.getCollectionRuns(),
                attempt.getPageCount(),
                attempt.getSuccessfulPageCount(),
                attempt.getDiscoveredQuestions(),
                attempt.getParsedQuestions(),
                attempt.getInitialNewQuestions(),
                attempt.getLatestNewQuestions(),
                attempt.getLatestDuplicateQuestions(),
                attempt.getRejectedQuestions(),
                attempt.isComplete(),
                attempt.getLastError());
    }

    private QuestionExportDocument.ExportSummary summarize(
            List<QuestionExportDocument.ExportQuestion> questions,
            List<QuestionExportDocument.ExportAttempt> attempts) {
        int revisions = questions.stream().mapToInt(q -> q.revisions().size()).sum();
        int keys = questions.stream()
                .flatMap(q -> q.revisions().stream())
                .mapToInt(revision -> revision.answerKeys().size())
                .sum();
        int conflicts = (int) questions.stream()
                .flatMap(q -> q.revisions().stream())
                .filter(QuestionExportDocument.ExportRevision::answerKeyConflict)
                .count();
        int origins = questions.stream().mapToInt(q -> q.origins().size()).sum();
        int observations = questions.stream()
                .flatMap(q -> q.revisions().stream())
                .flatMap(revision -> revision.answerKeys().stream())
                .mapToInt(key -> key.observations().size())
                .sum();
        int assets = questions.stream()
                .flatMap(q -> q.revisions().stream())
                .mapToInt(revision -> revision.assets().size())
                .sum();
        return new QuestionExportDocument.ExportSummary(
                questions.size(),
                revisions,
                keys,
                conflicts,
                origins,
                observations,
                assets,
                attempts.size());
    }

    private QuestionExportDocument.ExportFilter filterSnapshot(
            QuestionExportFilter filter) {
        return new QuestionExportDocument.ExportFilter(
                filter.quizIds().stream().sorted().toList(),
                filter.languages().stream().sorted().toList(),
                filter.questionTypes().stream()
                        .sorted(Comparator.comparing(QuestionType::name))
                        .toList(),
                filter.conflictsOnly(),
                filter.includeAttempts(),
                filter.query());
    }

    private boolean matchesScope(
            Long quizId, String language, QuestionExportFilter filter) {
        boolean quizMatches = filter.quizIds().isEmpty()
                || filter.quizIds().contains(quizId);
        boolean languageMatches = filter.languages().isEmpty()
                || filter.languages().contains(normalizedLanguage(language));
        return quizMatches && languageMatches;
    }

    private boolean matchesQuery(
            TestQuestion question,
            QuestionRevision revision,
            List<QuestionExportDocument.ExportAnswerKey> keys,
            List<QuestionAssetExportMetadata> assets,
            String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        values.add(question.getQuestionHash());
        values.add(question.getQuestionText());
        values.add(question.getContextText());
        values.add(revision.getRevisionHash());
        values.add(revision.getQuestionText());
        values.add(revision.getContextText());
        values.addAll(jsonConverter.getAnswersFromJson(
                revision.getPossibleAnswersJson()));
        keys.forEach(key -> {
            values.add(key.keyHash());
            values.addAll(key.correctAnswers());
        });
        assets.forEach(asset -> {
            values.add(asset.altText());
            values.add(asset.sourceUrl());
            values.add(asset.originalFileName());
            values.add(asset.contentHash());
        });
        return values.stream()
                .filter(value -> value != null)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle));
    }

    private String normalizedLanguage(String language) {
        return language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    }

    private static <T> Map<Long, List<T>> groupBy(
            List<T> values, java.util.function.Function<T, Long> classifier) {
        return values.stream().collect(Collectors.groupingBy(
                classifier,
                LinkedHashMap::new,
                Collectors.toList()));
    }

    private record ExportData(
            List<TestQuestion> questions,
            Map<Long, List<QuestionOrigin>> originsByQuestion,
            Map<Long, List<QuestionRevision>> revisionsByQuestion,
            Map<Long, List<AnswerKey>> keysByRevision,
            Map<Long, List<QuestionObservation>> observationsByKey,
            Map<Long, List<QuestionAssetExportMetadata>> assetsByRevision) {

        private static ExportData empty() {
            return new ExportData(
                    List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
