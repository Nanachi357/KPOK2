package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AnswerKey;
import com.myprojects.kpok2.model.QuestionOrigin;
import com.myprojects.kpok2.model.QuestionRevision;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.QuestionLibraryDetailDto;
import com.myprojects.kpok2.repository.AnswerKeyRepository;
import com.myprojects.kpok2.repository.QuestionOriginRepository;
import com.myprojects.kpok2.repository.QuestionRevisionRepository;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.util.JsonConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionLibraryDetailService {
    private final TestQuestionRepository questionRepository;
    private final QuestionRevisionRepository revisionRepository;
    private final AnswerKeyRepository answerKeyRepository;
    private final QuestionOriginRepository originRepository;
    private final JsonConverter jsonConverter;

    @Transactional(readOnly = true)
    public Optional<QuestionLibraryDetailDto> findByQuestionId(Long questionId) {
        if (questionId == null) {
            return Optional.empty();
        }
        return questionRepository.findById(questionId).map(this::toDetail);
    }

    private QuestionLibraryDetailDto toDetail(TestQuestion question) {
        List<QuestionRevision> revisions = revisionRepository
                .findAllByQuestionIdInOrderByQuestionIdAscIdAsc(
                        List.of(question.getId()));
        List<Long> revisionIds = revisions.stream()
                .map(QuestionRevision::getId)
                .toList();
        Map<Long, List<AnswerKey>> keysByRevision = revisionIds.isEmpty()
                ? Map.of()
                : answerKeyRepository
                        .findAllByRevisionIdInOrderByRevisionIdAscIdAsc(revisionIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                key -> key.getRevision().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()));
        List<QuestionOrigin> origins = originRepository
                .findAllByQuestionIdInOrderByQuestionIdAscQuizIdAscLanguageAsc(
                        List.of(question.getId()));

        return new QuestionLibraryDetailDto(
                question.getId(),
                question.getQuestionHash(),
                safe(question.getContextText()),
                safe(question.getQuestionText()),
                question.getQuestionType(),
                origins.stream().map(this::toOrigin).toList(),
                revisions.stream()
                        .map(revision -> toRevision(
                                revision,
                                keysByRevision.getOrDefault(
                                        revision.getId(), List.of())))
                        .toList());
    }

    private QuestionLibraryDetailDto.Origin toOrigin(QuestionOrigin origin) {
        return new QuestionLibraryDetailDto.Origin(
                origin.getQuizId(),
                safe(origin.getQuizUrl()),
                safe(origin.getLanguage()),
                origin.getFirstSeenAt(),
                origin.getLastSeenAt());
    }

    private QuestionLibraryDetailDto.Revision toRevision(
            QuestionRevision revision,
            List<AnswerKey> keys) {
        return new QuestionLibraryDetailDto.Revision(
                revision.getId(),
                revision.getRevisionHash(),
                safe(revision.getContextText()),
                safe(revision.getQuestionText()),
                jsonConverter.getAnswersFromJson(revision.getPossibleAnswersJson()),
                revision.getQuestionType(),
                revision.getFirstSeenAt(),
                revision.getLastSeenAt(),
                keys.size() > 1,
                keys.stream().map(this::toAnswerKey).toList());
    }

    private QuestionLibraryDetailDto.AnswerKey toAnswerKey(AnswerKey key) {
        return new QuestionLibraryDetailDto.AnswerKey(
                key.getId(),
                key.getKeyHash(),
                jsonConverter.getAnswersFromJson(key.getCorrectAnswersJson()),
                key.getFirstSeenAt(),
                key.getLastSeenAt());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
