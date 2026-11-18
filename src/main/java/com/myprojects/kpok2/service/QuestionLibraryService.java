package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.QuestionLibraryItemDto;
import com.myprojects.kpok2.model.dto.QuestionLibraryPageDto;
import com.myprojects.kpok2.model.dto.QuestionRecordDto;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionLibraryService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final TestQuestionRepository questionRepository;
    private final QuestionVersionService questionVersionService;

    @Transactional(readOnly = true)
    public QuestionLibraryPageDto findPage(String query, int requestedPage, int requestedSize) {
        int pageNumber = Math.max(0, requestedPage);
        int pageSize = normalizePageSize(requestedSize);
        String searchText = query == null ? "" : query.trim();

        Page<TestQuestion> page = questionRepository.findLibraryPage(
                searchText,
                PageRequest.of(pageNumber, pageSize));
        List<Long> questionIds = page.getContent().stream()
                .map(TestQuestion::getId)
                .toList();
        Map<Long, List<QuestionRecordDto>> recordsByQuestion = questionVersionService
                .getRecordsForQuestions(questionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        QuestionRecordDto::questionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<QuestionLibraryItemDto> items = page.getContent().stream()
                .map(question -> toItem(
                        question,
                        recordsByQuestion.getOrDefault(question.getId(), List.of())))
                .toList();

        return new QuestionLibraryPageDto(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private QuestionLibraryItemDto toItem(
            TestQuestion question,
            List<QuestionRecordDto> records) {
        long revisionCount = records.stream()
                .map(QuestionRecordDto::revisionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long answerKeyCount = records.stream()
                .map(QuestionRecordDto::answerKeyId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        QuestionRecordDto representative = records.isEmpty()
                ? null
                : records.get(records.size() - 1);
        QuestionType questionType = question.getQuestionType() != null
                ? question.getQuestionType()
                : representative == null ? null : representative.questionType();

        return new QuestionLibraryItemDto(
                question.getId(),
                firstNonBlank(
                        question.getContextText(),
                        representative == null ? null : representative.contextText()),
                firstNonBlank(
                        question.getQuestionText(),
                        representative == null ? null : representative.questionText()),
                questionType,
                Math.toIntExact(revisionCount),
                Math.toIntExact(answerKeyCount),
                records.stream().anyMatch(QuestionRecordDto::answerKeyConflict));
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank()
                ? fallback == null ? "" : fallback
                : preferred;
    }
}
