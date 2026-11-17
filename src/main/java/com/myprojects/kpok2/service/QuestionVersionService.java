package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionAsset;
import com.myprojects.kpok2.model.QuestionObservation;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.QuestionRevisionAsset;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedQuestionAssetDto;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.QuestionAssetRecordDto;
import com.myprojects.kpok2.model.dto.QuestionRecordDto;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.QuestionAssetRepository;
import com.myprojects.kpok2.repository.QuestionObservationRepository;
import com.myprojects.kpok2.repository.QuestionRevisionAssetRepository;
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
    private final QuestionAssetRepository assetRepository;
    private final QuestionRevisionAssetRepository revisionAssetRepository;
    private final JsonConverter jsonConverter;

    @Transactional
    public VersioningResult recordVersion(
            TestQuestion question, ParsedTestQuestionDto parsed) {
        LocalDateTime observedAt = LocalDateTime.now();
        String revisionHash = TestQuestionMapperUtils.generateRevisionHash(
                question.getQuestionHash(),
                parsed.getNormalizedAnswers(),
                parsed.getQuestionType(),
                parsed.getRevisionContentFingerprint());

        Optional<QuestionRevision> existingRevision = revisionRepository
                .findByQuestionIdAndRevisionHash(question.getId(), revisionHash);
        boolean newRevision = existingRevision.isEmpty();
        QuestionRevision revision = existingRevision.orElseGet(() ->
                revisionRepository.save(QuestionRevision.builder()
                        .question(question)
                        .revisionHash(revisionHash)
                        .contextText(parsed.getContextText())
                        .questionText(parsed.getQuestionText())
                        .contextHtml(parsed.getContextHtml())
                        .questionHtml(parsed.getQuestionHtml())
                        .possibleAnswersJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getAnswers()))
                        .normalizedAnswersJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getNormalizedAnswers()))
                        .answerHtmlJson(TestQuestionMapperUtils.answersToJson(
                                parsed.getAnswerHtml()))
                        .questionType(parsed.getQuestionType())
                        .firstSeenAt(observedAt)
                        .lastSeenAt(observedAt)
                        .build()));
        if (!newRevision) {
            revision.setLastSeenAt(observedAt);
            updateRichContent(revision, parsed);
            revisionRepository.save(revision);
        }

        persistAssets(revision, parsed.getAssets(), observedAt);

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
                result.revision().getContextText(),
                result.revision().getQuestionText(),
                jsonConverter.getAnswersFromJson(
                        result.revision().getPossibleAnswersJson()),
                jsonConverter.getAnswersFromJson(
                        result.answerKey().getCorrectAnswersJson()),
                result.revision().getQuestionType(),
                result.answerKeyConflict(),
                result.revision().getContextHtml(),
                result.revision().getQuestionHtml(),
                jsonConverter.getAnswersFromJson(
                        result.revision().getAnswerHtmlJson()),
                revisionAssetRepository
                        .findAllByRevisionIdOrderByContentLocationAscAnswerIndexAscSequenceIndexAsc(
                                result.revision().getId())
                        .stream()
                        .map(this::toAssetRecord)
                        .toList());
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
        Map<Long, List<QuestionRevisionAsset>> assetsByRevision = revisionAssetRepository
                .findAllByRevisionIdInOrderByRevisionIdAscContentLocationAscAnswerIndexAscSequenceIndexAsc(
                        revisionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        asset -> asset.getRevision().getId(),
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
                            conflict,
                            revision.getContextHtml(),
                            revision.getQuestionHtml(),
                            jsonConverter.getAnswersFromJson(
                                    revision.getAnswerHtmlJson()),
                            assetsByRevision.getOrDefault(revision.getId(), List.of())
                                    .stream()
                                    .map(this::toAssetRecord)
                                    .toList()));
                })
                .toList();
    }

    private void updateRichContent(
            QuestionRevision revision, ParsedTestQuestionDto parsed) {
        if (!isBlank(parsed.getContextHtml())) {
            revision.setContextHtml(parsed.getContextHtml());
        }
        if (!isBlank(parsed.getQuestionHtml())) {
            revision.setQuestionHtml(parsed.getQuestionHtml());
        }
        if (parsed.getAnswerHtml() != null && !parsed.getAnswerHtml().isEmpty()) {
            revision.setAnswerHtmlJson(TestQuestionMapperUtils.answersToJson(
                    parsed.getAnswerHtml()));
        }
    }

    private void persistAssets(
            QuestionRevision revision,
            List<ParsedQuestionAssetDto> parsedAssets,
            LocalDateTime observedAt) {
        if (parsedAssets == null || parsedAssets.isEmpty()) {
            return;
        }

        for (ParsedQuestionAssetDto parsedAsset : parsedAssets) {
            QuestionAsset storedAsset = resolveAsset(parsedAsset, observedAt);
            AssetFetchStatus status = parsedAsset.getFetchStatus() == null
                    ? AssetFetchStatus.FAILED
                    : parsedAsset.getFetchStatus();
            String failureReason = parsedAsset.getFailureReason();
            if (status == AssetFetchStatus.STORED && storedAsset == null) {
                status = AssetFetchStatus.FAILED;
                failureReason = "Image content was unavailable for storage";
            }

            String referenceKey = referenceKey(parsedAsset);
            AssetFetchStatus finalStatus = status;
            String finalFailureReason = failureReason;
            QuestionAsset finalStoredAsset = storedAsset;
            revisionAssetRepository
                    .findByRevisionIdAndReferenceKey(revision.getId(), referenceKey)
                    .ifPresentOrElse(existing -> {
                        if (finalStoredAsset != null) {
                            existing.setAsset(finalStoredAsset);
                        }
                        existing.setSourceUrl(safe(parsedAsset.getSourceUrl()));
                        existing.setAltText(safe(parsedAsset.getAltText()));
                        existing.setFetchStatus(finalStatus);
                        existing.setFailureReason(finalFailureReason);
                        existing.setLastSeenAt(observedAt);
                        revisionAssetRepository.save(existing);
                    }, () -> revisionAssetRepository.save(
                            QuestionRevisionAsset.builder()
                                    .revision(revision)
                                    .asset(finalStoredAsset)
                                    .referenceKey(referenceKey)
                                    .contentLocation(parsedAsset.getContentLocation())
                                    .answerIndex(parsedAsset.getAnswerIndex())
                                    .sequenceIndex(parsedAsset.getSequenceIndex())
                                    .sourceUrl(safe(parsedAsset.getSourceUrl()))
                                    .altText(safe(parsedAsset.getAltText()))
                                    .fetchStatus(finalStatus)
                                    .failureReason(finalFailureReason)
                                    .firstSeenAt(observedAt)
                                    .lastSeenAt(observedAt)
                                    .build()));
        }
    }

    private QuestionAsset resolveAsset(
            ParsedQuestionAssetDto parsedAsset, LocalDateTime observedAt) {
        if (parsedAsset.getFetchStatus() != AssetFetchStatus.STORED
                || isBlank(parsedAsset.getContentHash())) {
            return null;
        }
        Optional<QuestionAsset> existing = assetRepository.findByContentHash(
                parsedAsset.getContentHash());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (parsedAsset.getContent() == null || parsedAsset.getContent().length == 0) {
            return null;
        }
        return assetRepository.save(QuestionAsset.builder()
                .contentHash(parsedAsset.getContentHash())
                .mediaType(safe(parsedAsset.getMediaType()))
                .originalFileName(safe(parsedAsset.getOriginalFileName()))
                .contentLength(parsedAsset.getContent().length)
                .content(parsedAsset.getContent())
                .createdAt(observedAt)
                .build());
    }

    private QuestionAssetRecordDto toAssetRecord(QuestionRevisionAsset reference) {
        QuestionAsset asset = reference.getAsset();
        return new QuestionAssetRecordDto(
                reference.getId(),
                asset == null ? null : asset.getId(),
                asset == null ? null : asset.getContentHash(),
                asset == null ? null : asset.getMediaType(),
                asset == null ? null : asset.getOriginalFileName(),
                asset == null ? 0 : asset.getContentLength(),
                reference.getContentLocation(),
                reference.getAnswerIndex(),
                reference.getSequenceIndex(),
                reference.getSourceUrl(),
                reference.getAltText(),
                reference.getFetchStatus(),
                reference.getFailureReason(),
                asset == null ? null : "/api/assets/" + asset.getId());
    }

    private String referenceKey(ParsedQuestionAssetDto asset) {
        return asset.getContentLocation() + ":"
                + asset.getAnswerIndex() + ":" + asset.getSequenceIndex();
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record VersioningResult(
            QuestionRevision revision,
            AnswerKey answerKey,
            boolean newRevision,
            boolean newAnswerKey,
            boolean answerKeyConflict) {
    }
}
