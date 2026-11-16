package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.QuestionRecordDto;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapperUtils;
import com.myprojects.kpok2.util.JsonConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Persists option revisions, answer keys, and their attempt-level observations. */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionVersionService {

    private static final String UNKNOWN_ATTEMPT = "unknown";

    private final QuestionRevisionRepository revisionRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final QuestionObservationRepository observationRepository;
    private final JsonConverter jsonConverter;

    @Transactional
    public VersioningResult recordVersion(
            TestQuestion question, ParsedTestQuestionDto parsed) {
        LocalDateTime observedAt = LocalDateTime.now();
        String revisionHash = TestQuestionMapperUtils.generateRevisionHash(
                question.getQuestionHash(),
                parsed.getNormalizedAnswers(),
                parsed.getQuestionType());

        Optional<QuestionRevision> existingRevision = revisionRepository
                .findByQuestionIdAndRevisionHash(question.getId(), revisionHash);
        boolean newRevision = existingRevision.isEmpty();
        QuestionRevision revision = existingRevision.orElseGet(() ->
                revisionRepository.save(QuestionRevision.builder()
                        .question(question)
                        .revisionHash(revisionHash)
                        .contextText(parsed.getContextText())
                        .questionText(parsed.getQuestionText())
                        .possibleAnswersJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getAnswers()))
                        .normalizedAnswersJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getNormalizedAnswers()))
                        .questionType(parsed.getQuestionType())
                        .firstSeenAt(observedAt)
                        .lastSeenAt(observedAt)
                        .build()));
        if (!newRevision) {
            revision.setLastSeenAt(observedAt);
            revisionRepository.save(revision);
        }

        String keyHash = TestQuestionMapperUtils.generateAnswerKeyHash(
                parsed.getNormalizedCorrectAnswers());
        Optional<AnswerKey> existingKey = answerKeyRepository
                .findByRevisionIdAndKeyHash(revision.getId(), keyHash);
        boolean newKey = existingKey.isEmpty();
        boolean keyConflict = newKey
                && answerKeyRepository.countByRevisionId(revision.getId()) > 0;
        AnswerKey answerKey = existingKey.orElseGet(() ->
                answerKeyRepository.save(AnswerKey.builder()
                        .revision(revision)
                        .keyHash(keyHash)
                        .correctAnswersJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getCorrectAnswers()))
                        .normalizedCorrectAnswersJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getNormalizedCorrectAnswers()))
                        .firstSeenAt(observedAt)
                        .lastSeenAt(observedAt)
                        .build()));
        if (!newKey) {
            answerKey.setLastSeenAt(observedAt);
            answerKeyRepository.save(answerKey);
        }

        recordObservation(question, revision, answerKey, parsed, observedAt);

        if (keyConflict) {
            log.warn(
                    "Conflicting answer key observed for question {} revision {} in quiz {} attempt {}",
                    question.getId(),
                    revision.getId(),
                    parsed.getSourceQuizId(),
                    safeAttempt(parsed.getSourceAttemptId()));
        }

        return new VersioningResult(
                revision, answerKey, newRevision, newKey, keyConflict);
    }

    public QuestionRecordDto toRecord(
            TestQuestion question,
            ParsedTestQuestionDto parsed,
            VersioningResult result) {
        return new QuestionRecordDto(
                question.getId(),
                result.revision().getId(),
                result.answerKey().getId(),
                parsed.getContextText(),
                parsed.getQuestionText(),
                parsed.getAnswers(),
                parsed.getCorrectAnswers(),
                parsed.getQuestionType(),
                result.answerKeyConflict());
    }

    @Transactional(readOnly = true)
    public List<QuestionRecordDto> getAllRecords() {
        return records(revisionRepository.findAllByOrderByQuestionIdAscIdAsc());
    }

    @Transactional(readOnly = true)
    public List<QuestionRecordDto> getRecordsForQuestions(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return records(revisionRepository
                .findAllByQuestionIdInOrderByQuestionIdAscIdAsc(questionIds));
    }

    @Transactional(readOnly = true)
    public List<QuestionRecordDto> searchRecords(String searchText) {
        return records(revisionRepository.searchRecords(searchText));
    }

    private List<QuestionRecordDto> records(List<QuestionRevision> revisions) {
        if (revisions.isEmpty()) {
            return List.of();
        }

        List<Long> revisionIds = revisions.stream()
                .map(QuestionRevision::getId)
                .toList();
        Map<Long, List<AnswerKey>> keysByRevision = answerKeyRepository
                .findAllByRevisionIdInOrderByRevisionIdAscIdAsc(revisionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        key -> key.getRevision().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return revisions.stream()
                .flatMap(revision -> {
                    List<AnswerKey> keys = keysByRevision.getOrDefault(
                            revision.getId(), List.of());
                    boolean conflict = keys.size() > 1;
                    return keys.stream().map(key -> new QuestionRecordDto(
                            revision.getQuestion().getId(),
                            revision.getId(),
                            key.getId(),
                            revision.getContextText(),
                            revision.getQuestionText(),
                            jsonConverter.getAnswersFromJson(
                                    revision.getPossibleAnswersJson()),
                            jsonConverter.getAnswersFromJson(
                                    key.getCorrectAnswersJson()),
                            revision.getQuestionType(),
                            conflict));
                })
                .toList();
    }

    private void recordObservation(
            TestQuestion question,
            QuestionRevision revision,
            AnswerKey answerKey,
            ParsedTestQuestionDto parsed,
            LocalDateTime observedAt) {
        if (parsed.getSourceQuizId() == null) {
            return;
        }

        String attemptId = safeAttempt(parsed.getSourceAttemptId());
        String language = QuestionOriginService.normalizeLanguage(
                parsed.getSourceLanguage());
        observationRepository
                .findByRevisionIdAndAnswerKeyIdAndQuizIdAndSourceAttemptIdAndLanguage(
                        revision.getId(),
                        answerKey.getId(),
                        parsed.getSourceQuizId(),
                        attemptId,
                        language)
                .ifPresentOrElse(existing -> {
                    existing.setLastSeenAt(observedAt);
                    existing.setObservationCount(existing.getObservationCount() + 1);
                    if (existing.getQuizUrl().isBlank() && parsed.getSourceUrl() != null) {
                        existing.setQuizUrl(parsed.getSourceUrl().trim());
                    }
                    observationRepository.save(existing);
                }, () -> observationRepository.save(QuestionObservation.builder()
                        .question(question)
                        .revision(revision)
                        .answerKey(answerKey)
                        .quizId(parsed.getSourceQuizId())
                        .quizUrl(safe(parsed.getSourceUrl()))
                        .sourceAttemptId(attemptId)
                        .language(language)
                        .firstSeenAt(observedAt)
                        .lastSeenAt(observedAt)
                        .observationCount(1)
                        .build()));
    }

    private static String safeAttempt(String attemptId) {
        return attemptId == null || attemptId.isBlank()
                ? UNKNOWN_ATTEMPT
                : attemptId.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record VersioningResult(
            QuestionRevision revision,
            AnswerKey answerKey,
            boolean newRevision,
            boolean newAnswerKey,
            boolean answerKeyConflict) {
    }
}
