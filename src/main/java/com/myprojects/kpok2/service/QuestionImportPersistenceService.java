package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.CollectionAttempt;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.CollectionAttemptRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapperUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Atomically merges a validated export into the local normalized question store. */
@Service
@RequiredArgsConstructor
public class QuestionImportPersistenceService {

    private final QuestionImportService importService;
    private final TestQuestionRepository questionRepository;
    private final QuestionRevisionRepository revisionRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final QuestionOriginRepository originRepository;
    private final QuestionObservationRepository observationRepository;
    private final CollectionAttemptRepository attemptRepository;
    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock importLock = new ReentrantLock(true);

    public QuestionImportResult importFile(Path source) throws IOException {
        return mergeAtomically(importService.read(source).document());
    }

    public QuestionImportResult importDocument(QuestionExportDocument document) {
        importService.inspect(document);
        return mergeAtomically(document);
    }

    private QuestionImportResult mergeAtomically(QuestionExportDocument document) {
        importLock.lock();
        try {
            QuestionImportResult result = transactionTemplate.execute(
                    ignored -> merge(document));
            if (result == null) {
                throw new IllegalStateException("Question import transaction returned no result");
            }
            return result;
        } finally {
            importLock.unlock();
        }
    }

    private QuestionImportResult merge(QuestionExportDocument document) {
        MutableResult result = new MutableResult(document.schemaVersion());
        Map<QuestionExportDocument.ExportQuestion, TestQuestion> questions =
                mergeQuestions(document.questions(), result);
        mergeOrigins(document.questions(), questions, result);
        Map<QuestionExportDocument.ExportRevision, QuestionRevision> revisions =
                mergeRevisions(document.questions(), questions, result);
        Map<QuestionExportDocument.ExportAnswerKey, AnswerKey> keys =
                mergeAnswerKeys(document.questions(), revisions, result);
        mergeObservations(document.questions(), questions, revisions, keys, result);
        mergeAttempts(document.attempts(), result);
        result.assetsSkipped = document.summary().assets();
        return result.toResult();
    }

    private Map<QuestionExportDocument.ExportQuestion, TestQuestion> mergeQuestions(
            List<QuestionExportDocument.ExportQuestion> sourceQuestions,
            MutableResult result) {
        Set<String> hashes = sourceQuestions.stream()
                .map(QuestionExportDocument.ExportQuestion::questionHash)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, TestQuestion> existingByHash = hashes.isEmpty()
                ? new HashMap<>()
                : questionRepository.findAllByQuestionHashIn(hashes).stream()
                .collect(Collectors.toMap(
                        TestQuestion::getQuestionHash,
                        Function.identity()));
        Map<QuestionExportDocument.ExportQuestion, TestQuestion> resolved =
                new IdentityHashMap<>();
        List<TestQuestion> toSave = new ArrayList<>();

        for (var source : sourceQuestions) {
            TestQuestion question = existingByHash.get(source.questionHash());
            if (question == null) {
                question = newQuestion(source);
                existingByHash.put(source.questionHash(), question);
                result.questionsCreated++;
                toSave.add(question);
            } else {
                result.questionsMerged++;
                if (mergeQuestion(question, source)) {
                    toSave.add(question);
                }
            }
            resolved.put(source, question);
        }
        if (!toSave.isEmpty()) {
            questionRepository.saveAll(toSave);
        }
        return resolved;
    }

    private TestQuestion newQuestion(
            QuestionExportDocument.ExportQuestion source) {
        var firstRevision = source.revisions().get(0);
        var firstKey = firstRevision.answerKeys().get(0);
        var firstOrigin = source.origins().isEmpty()
                ? null
                : source.origins().get(0);
        return TestQuestion.builder()
                .questionText(source.questionText())
                .normalizedText(source.normalizedText())
                .contextText(source.contextText())
                .normalizedContext(source.normalizedContext())
                .possibleAnswersJson(json(firstRevision.answers()))
                .correctAnswersJson(json(firstKey.correctAnswers()))
                .correctAnswer(firstKey.correctAnswers().get(0))
                .normalizedCorrectAnswer(
                        firstKey.normalizedCorrectAnswers().get(0))
                .normalizationVersion(source.normalizationVersion())
                .questionType(source.questionType())
                .sourceQuizId(firstOrigin == null ? null : firstOrigin.quizId())
                .sourceUrl(firstOrigin == null ? null : firstOrigin.quizUrl())
                .sourceLanguage(firstOrigin == null
                        ? null
                        : normalizedLanguage(firstOrigin.language()))
                .questionHash(source.questionHash())
                .parsedAt(source.parsedAt())
                .build();
    }

    private boolean mergeQuestion(
            TestQuestion target,
            QuestionExportDocument.ExportQuestion source) {
        boolean changed = false;
        changed |= fill(target.getQuestionText(), source.questionText(),
                target::setQuestionText);
        changed |= fill(target.getNormalizedText(), source.normalizedText(),
                target::setNormalizedText);
        changed |= fill(target.getContextText(), source.contextText(),
                target::setContextText);
        changed |= fill(target.getNormalizedContext(), source.normalizedContext(),
                target::setNormalizedContext);
        if (target.getQuestionType() == null) {
            target.setQuestionType(source.questionType());
            changed = true;
        }
        if (target.getNormalizationVersion() == null
                || target.getNormalizationVersion() < source.normalizationVersion()) {
            target.setNormalizationVersion(source.normalizationVersion());
            changed = true;
        }
        LocalDateTime parsedAt = earliest(target.getParsedAt(), source.parsedAt());
        if (!java.util.Objects.equals(parsedAt, target.getParsedAt())) {
            target.setParsedAt(parsedAt);
            changed = true;
        }
        var firstRevision = source.revisions().get(0);
        var firstKey = firstRevision.answerKeys().get(0);
        changed |= fill(target.getPossibleAnswersJson(), json(firstRevision.answers()),
                target::setPossibleAnswersJson);
        changed |= fill(target.getCorrectAnswersJson(), json(firstKey.correctAnswers()),
                target::setCorrectAnswersJson);
        changed |= fill(target.getCorrectAnswer(), firstKey.correctAnswers().get(0),
                target::setCorrectAnswer);
        changed |= fill(
                target.getNormalizedCorrectAnswer(),
                firstKey.normalizedCorrectAnswers().get(0),
                target::setNormalizedCorrectAnswer);
        if (!source.origins().isEmpty()) {
            var origin = source.origins().get(0);
            if (target.getSourceQuizId() == null) {
                target.setSourceQuizId(origin.quizId());
                changed = true;
            }
            changed |= fill(target.getSourceUrl(), origin.quizUrl(),
                    target::setSourceUrl);
            changed |= fill(
                    target.getSourceLanguage(),
                    normalizedLanguage(origin.language()),
                    target::setSourceLanguage);
        }
        return changed;
    }

    private void mergeOrigins(
            List<QuestionExportDocument.ExportQuestion> sourceQuestions,
            Map<QuestionExportDocument.ExportQuestion, TestQuestion> questions,
            MutableResult result) {
        List<Long> questionIds = entityIds(questions.values(), TestQuestion::getId);
        Map<OriginIdentity, QuestionOrigin> existing = questionIds.isEmpty()
                ? new HashMap<>()
                : originRepository
                .findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                        questionIds)
                .stream()
                .collect(Collectors.toMap(
                        origin -> new OriginIdentity(
                                origin.getQuestion().getId(),
                                origin.getQuizId(),
                                normalizedLanguage(origin.getLanguage())),
                        Function.identity()));
        List<QuestionOrigin> toSave = new ArrayList<>();
        for (var sourceQuestion : sourceQuestions) {
            TestQuestion question = questions.get(sourceQuestion);
            for (var source : sourceQuestion.origins()) {
                OriginIdentity identity = new OriginIdentity(
                        question.getId(),
                        source.quizId(),
                        normalizedLanguage(source.language()));
                QuestionOrigin origin = existing.get(identity);
                if (origin == null) {
                    origin = QuestionOrigin.builder()
                            .question(question)
                            .quizId(source.quizId())
                            .quizUrl(source.quizUrl())
                            .language(identity.language())
                            .firstSeenAt(source.firstSeenAt())
                            .lastSeenAt(source.lastSeenAt())
                            .build();
                    existing.put(identity, origin);
                    result.originsCreated++;
                    toSave.add(origin);
                } else {
                    result.originsMerged++;
                    mergeTimeRange(
                            origin.getFirstSeenAt(),
                            origin.getLastSeenAt(),
                            source.firstSeenAt(),
                            source.lastSeenAt(),
                            origin::setFirstSeenAt,
                            origin::setLastSeenAt);
                    fill(origin.getQuizUrl(), source.quizUrl(), origin::setQuizUrl);
                    toSave.add(origin);
                }
            }
        }
        if (!toSave.isEmpty()) {
            originRepository.saveAll(toSave);
        }
    }

    private Map<QuestionExportDocument.ExportRevision, QuestionRevision> mergeRevisions(
            List<QuestionExportDocument.ExportQuestion> sourceQuestions,
            Map<QuestionExportDocument.ExportQuestion, TestQuestion> questions,
            MutableResult result) {
        List<Long> questionIds = entityIds(questions.values(), TestQuestion::getId);
        Map<RevisionIdentity, QuestionRevision> existing = questionIds.isEmpty()
                ? new HashMap<>()
                : revisionRepository
                .findAllByQuestionIdInOrderByQuestionIdAscIdAsc(questionIds)
                .stream()
                .collect(Collectors.toMap(
                        revision -> new RevisionIdentity(
                                revision.getQuestion().getId(),
                                revision.getRevisionHash()),
                        Function.identity()));
        Map<QuestionExportDocument.ExportRevision, QuestionRevision> resolved =
                new IdentityHashMap<>();
        List<QuestionRevision> toSave = new ArrayList<>();
        for (var sourceQuestion : sourceQuestions) {
            TestQuestion question = questions.get(sourceQuestion);
            for (var source : sourceQuestion.revisions()) {
                RevisionIdentity identity = new RevisionIdentity(
                        question.getId(), source.revisionHash());
                QuestionRevision revision = existing.get(identity);
                if (revision == null) {
                    revision = QuestionRevision.builder()
                            .question(question)
                            .revisionHash(source.revisionHash())
                            .contextText(source.contextText())
                            .questionText(source.questionText())
                            .contextHtml(source.contextHtml())
                            .questionHtml(source.questionHtml())
                            .possibleAnswersJson(json(source.answers()))
                            .normalizedAnswersJson(json(source.normalizedAnswers()))
                            .answerHtmlJson(json(source.answerHtml()))
                            .questionType(source.questionType())
                            .firstSeenAt(source.firstSeenAt())
                            .lastSeenAt(source.lastSeenAt())
                            .build();
                    existing.put(identity, revision);
                    result.revisionsCreated++;
                } else {
                    result.revisionsMerged++;
                    mergeRevision(revision, source);
                }
                resolved.put(source, revision);
                toSave.add(revision);
            }
        }
        if (!toSave.isEmpty()) {
            revisionRepository.saveAll(toSave);
        }
        return resolved;
    }

    private void mergeRevision(
            QuestionRevision target,
            QuestionExportDocument.ExportRevision source) {
        fill(target.getContextText(), source.contextText(), target::setContextText);
        fill(target.getQuestionText(), source.questionText(), target::setQuestionText);
        fill(target.getContextHtml(), source.contextHtml(), target::setContextHtml);
        fill(target.getQuestionHtml(), source.questionHtml(), target::setQuestionHtml);
        fill(target.getPossibleAnswersJson(), json(source.answers()),
                target::setPossibleAnswersJson);
        fill(target.getNormalizedAnswersJson(), json(source.normalizedAnswers()),
                target::setNormalizedAnswersJson);
        fill(target.getAnswerHtmlJson(), json(source.answerHtml()),
                target::setAnswerHtmlJson);
        mergeTimeRange(
                target.getFirstSeenAt(),
                target.getLastSeenAt(),
                source.firstSeenAt(),
                source.lastSeenAt(),
                target::setFirstSeenAt,
                target::setLastSeenAt);
    }

    private Map<QuestionExportDocument.ExportAnswerKey, AnswerKey> mergeAnswerKeys(
            List<QuestionExportDocument.ExportQuestion> sourceQuestions,
            Map<QuestionExportDocument.ExportRevision, QuestionRevision> revisions,
            MutableResult result) {
        List<Long> revisionIds = entityIds(
                revisions.values(), QuestionRevision::getId);
        Map<KeyIdentity, AnswerKey> existing = revisionIds.isEmpty()
                ? new HashMap<>()
                : answerKeyRepository
                .findAllByRevisionIdInOrderByRevisionIdAscIdAsc(revisionIds)
                .stream()
                .collect(Collectors.toMap(
                        key -> new KeyIdentity(
                                key.getRevision().getId(), key.getKeyHash()),
                        Function.identity()));
        Map<QuestionExportDocument.ExportAnswerKey, AnswerKey> resolved =
                new IdentityHashMap<>();
        List<AnswerKey> toSave = new ArrayList<>();
        for (var sourceQuestion : sourceQuestions) {
            for (var sourceRevision : sourceQuestion.revisions()) {
                QuestionRevision revision = revisions.get(sourceRevision);
                for (var source : sourceRevision.answerKeys()) {
                    KeyIdentity identity = new KeyIdentity(
                            revision.getId(), source.keyHash());
                    AnswerKey key = existing.get(identity);
                    if (key == null) {
                        key = AnswerKey.builder()
                                .revision(revision)
                                .keyHash(source.keyHash())
                                .correctAnswersJson(json(source.correctAnswers()))
                                .normalizedCorrectAnswersJson(json(
                                        source.normalizedCorrectAnswers()))
                                .firstSeenAt(source.firstSeenAt())
                                .lastSeenAt(source.lastSeenAt())
                                .build();
                        existing.put(identity, key);
                        result.answerKeysCreated++;
                    } else {
                        result.answerKeysMerged++;
                        fill(key.getCorrectAnswersJson(), json(source.correctAnswers()),
                                key::setCorrectAnswersJson);
                        fill(
                                key.getNormalizedCorrectAnswersJson(),
                                json(source.normalizedCorrectAnswers()),
                                key::setNormalizedCorrectAnswersJson);
                        mergeTimeRange(
                                key.getFirstSeenAt(),
                                key.getLastSeenAt(),
                                source.firstSeenAt(),
                                source.lastSeenAt(),
                                key::setFirstSeenAt,
                                key::setLastSeenAt);
                    }
                    resolved.put(source, key);
                    toSave.add(key);
                }
            }
        }
        if (!toSave.isEmpty()) {
            answerKeyRepository.saveAll(toSave);
        }
        return resolved;
    }

    private void mergeObservations(
            List<QuestionExportDocument.ExportQuestion> sourceQuestions,
            Map<QuestionExportDocument.ExportQuestion, TestQuestion> questions,
            Map<QuestionExportDocument.ExportRevision, QuestionRevision> revisions,
            Map<QuestionExportDocument.ExportAnswerKey, AnswerKey> keys,
            MutableResult result) {
        List<Long> revisionIds = entityIds(
                revisions.values(), QuestionRevision::getId);
        Map<ObservationIdentity, QuestionObservation> existing =
                revisionIds.isEmpty()
                        ? new HashMap<>()
                        : observationRepository
                        .findAllByRevisionIdInOrderByRevisionIdAscAnswerKeyIdAscIdAsc(
                                revisionIds)
                        .stream()
                        .collect(Collectors.toMap(
                                observation -> new ObservationIdentity(
                                        observation.getRevision().getId(),
                                        observation.getAnswerKey().getId(),
                                        observation.getQuizId(),
                                        observation.getSourceAttemptId(),
                                        normalizedLanguage(observation.getLanguage())),
                                Function.identity()));
        List<QuestionObservation> toSave = new ArrayList<>();
        for (var sourceQuestion : sourceQuestions) {
            TestQuestion question = questions.get(sourceQuestion);
            for (var sourceRevision : sourceQuestion.revisions()) {
                QuestionRevision revision = revisions.get(sourceRevision);
                for (var sourceKey : sourceRevision.answerKeys()) {
                    AnswerKey key = keys.get(sourceKey);
                    for (var source : sourceKey.observations()) {
                        ObservationIdentity identity = new ObservationIdentity(
                                revision.getId(),
                                key.getId(),
                                source.quizId(),
                                source.sourceAttemptId(),
                                normalizedLanguage(source.language()));
                        QuestionObservation observation = existing.get(identity);
                        if (observation == null) {
                            observation = QuestionObservation.builder()
                                    .question(question)
                                    .revision(revision)
                                    .answerKey(key)
                                    .quizId(source.quizId())
                                    .quizUrl(source.quizUrl())
                                    .sourceAttemptId(source.sourceAttemptId())
                                    .language(identity.language())
                                    .firstSeenAt(source.firstSeenAt())
                                    .lastSeenAt(source.lastSeenAt())
                                    .observationCount(source.observationCount())
                                    .build();
                            existing.put(identity, observation);
                            result.observationsCreated++;
                        } else {
                            result.observationsMerged++;
                            fill(observation.getQuizUrl(), source.quizUrl(),
                                    observation::setQuizUrl);
                            mergeTimeRange(
                                    observation.getFirstSeenAt(),
                                    observation.getLastSeenAt(),
                                    source.firstSeenAt(),
                                    source.lastSeenAt(),
                                    observation::setFirstSeenAt,
                                    observation::setLastSeenAt);
                            observation.setObservationCount(Math.max(
                                    observation.getObservationCount(),
                                    source.observationCount()));
                        }
                        toSave.add(observation);
                    }
                }
            }
        }
        if (!toSave.isEmpty()) {
            observationRepository.saveAll(toSave);
        }
    }

    private void mergeAttempts(
            List<QuestionExportDocument.ExportAttempt> sourceAttempts,
            MutableResult result) {
        Set<Long> quizIds = sourceAttempts.stream()
                .map(QuestionExportDocument.ExportAttempt::quizId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<AttemptIdentity, CollectionAttempt> existing = quizIds.isEmpty()
                ? new HashMap<>()
                : attemptRepository
                .findAllByQuizIdInOrderByQuizIdAscIdAsc(quizIds)
                .stream()
                .collect(Collectors.toMap(
                        attempt -> new AttemptIdentity(
                                attempt.getQuizId(), attempt.getSourceAttemptId()),
                        Function.identity()));
        List<CollectionAttempt> toSave = new ArrayList<>();
        for (var source : sourceAttempts) {
            AttemptIdentity identity = new AttemptIdentity(
                    source.quizId(), source.sourceAttemptId());
            CollectionAttempt attempt = existing.get(identity);
            if (attempt == null) {
                attempt = newAttempt(source);
                existing.put(identity, attempt);
                result.attemptsCreated++;
            } else {
                result.attemptsMerged++;
                mergeAttempt(attempt, source);
            }
            toSave.add(attempt);
        }
        if (!toSave.isEmpty()) {
            attemptRepository.saveAll(toSave);
        }
    }

    private CollectionAttempt newAttempt(
            QuestionExportDocument.ExportAttempt source) {
        return CollectionAttempt.builder()
                .quizId(source.quizId())
                .sourceAttemptId(source.sourceAttemptId())
                .quizUrl(source.quizUrl())
                .reviewUrl(source.reviewUrl())
                .firstCollectedAt(source.firstCollectedAt())
                .lastCollectedAt(source.lastCollectedAt())
                .collectionRuns(source.collectionRuns())
                .pageCount(source.pageCount())
                .successfulPageCount(source.successfulPageCount())
                .discoveredQuestions(source.discoveredQuestions())
                .parsedQuestions(source.parsedQuestions())
                .initialNewQuestions(source.initialNewQuestions())
                .latestNewQuestions(source.latestNewQuestions())
                .latestDuplicateQuestions(source.latestDuplicateQuestions())
                .rejectedQuestions(source.rejectedQuestions())
                .complete(source.complete())
                .lastError(source.lastError())
                .build();
    }

    private void mergeAttempt(
            CollectionAttempt target,
            QuestionExportDocument.ExportAttempt source) {
        LocalDateTime previousFirst = target.getFirstCollectedAt();
        LocalDateTime previousLast = target.getLastCollectedAt();
        boolean sourceIsFirst = source.firstCollectedAt().isBefore(previousFirst);
        boolean sourceIsLatest = !source.lastCollectedAt().isBefore(previousLast);
        target.setFirstCollectedAt(earliest(previousFirst, source.firstCollectedAt()));
        target.setLastCollectedAt(latest(previousLast, source.lastCollectedAt()));
        target.setCollectionRuns(Math.max(
                target.getCollectionRuns(), source.collectionRuns()));
        if (sourceIsFirst) {
            target.setInitialNewQuestions(source.initialNewQuestions());
        }
        if (sourceIsLatest) {
            target.setQuizUrl(source.quizUrl());
            target.setReviewUrl(source.reviewUrl());
            target.setPageCount(source.pageCount());
            target.setSuccessfulPageCount(source.successfulPageCount());
            target.setDiscoveredQuestions(source.discoveredQuestions());
            target.setParsedQuestions(source.parsedQuestions());
            target.setLatestNewQuestions(source.latestNewQuestions());
            target.setLatestDuplicateQuestions(source.latestDuplicateQuestions());
            target.setRejectedQuestions(source.rejectedQuestions());
            target.setComplete(source.complete());
            target.setLastError(source.lastError());
        }
    }

    private String json(List<String> values) {
        return TestQuestionMapperUtils.answersToJson(values);
    }

    private static <T> List<Long> entityIds(
            Collection<T> entities, Function<T, Long> idExtractor) {
        return entities.stream()
                .map(idExtractor)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean fill(
            String current,
            String incoming,
            java.util.function.Consumer<String> setter) {
        if ((current == null || current.isBlank())
                && incoming != null && !incoming.isBlank()) {
            setter.accept(incoming);
            return true;
        }
        return false;
    }

    private void mergeTimeRange(
            LocalDateTime currentFirst,
            LocalDateTime currentLast,
            LocalDateTime incomingFirst,
            LocalDateTime incomingLast,
            java.util.function.Consumer<LocalDateTime> firstSetter,
            java.util.function.Consumer<LocalDateTime> lastSetter) {
        firstSetter.accept(earliest(currentFirst, incomingFirst));
        lastSetter.accept(latest(currentLast, incomingLast));
    }

    private LocalDateTime earliest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isBefore(second) ? first : second;
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }

    private static String normalizedLanguage(String value) {
        return value == null || value.isBlank()
                ? "und"
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private record OriginIdentity(Long questionId, Long quizId, String language) {
    }

    private record RevisionIdentity(Long questionId, String hash) {
    }

    private record KeyIdentity(Long revisionId, String hash) {
    }

    private record ObservationIdentity(
            Long revisionId,
            Long answerKeyId,
            Long quizId,
            String attemptId,
            String language) {
    }

    private record AttemptIdentity(Long quizId, String attemptId) {
    }

    private static final class MutableResult {
        private final String schemaVersion;
        private int questionsCreated;
        private int questionsMerged;
        private int revisionsCreated;
        private int revisionsMerged;
        private int answerKeysCreated;
        private int answerKeysMerged;
        private int originsCreated;
        private int originsMerged;
        private int observationsCreated;
        private int observationsMerged;
        private int attemptsCreated;
        private int attemptsMerged;
        private int assetsSkipped;

        private MutableResult(String schemaVersion) {
            this.schemaVersion = schemaVersion;
        }

        private QuestionImportResult toResult() {
            return new QuestionImportResult(
                    schemaVersion,
                    questionsCreated,
                    questionsMerged,
                    revisionsCreated,
                    revisionsMerged,
                    answerKeysCreated,
                    answerKeysMerged,
                    originsCreated,
                    originsMerged,
                    observationsCreated,
                    observationsMerged,
                    attemptsCreated,
                    attemptsMerged,
                    assetsSkipped);
        }
    }
}
