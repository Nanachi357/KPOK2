package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapper;
import com.myprojects.kpok2.service.mapper.TestQuestionMapperUtils;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import com.myprojects.kpok2.util.JsonConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestQuestionService {
    private final TestQuestionRepository repository;
    private final TestQuestionMapper testQuestionMapper;
    private final TestParsingStatistics parsingStatistics;
    private final JsonConverter jsonConverter;
    
    private final List<TestQuestionListener> listeners = new CopyOnWriteArrayList<>();
    
    public interface TestQuestionListener {
        void onNewQuestion(TestQuestion question, List<String> answers);
    }
    
    public void addListener(TestQuestionListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(TestQuestionListener listener) {
        listeners.remove(listener);
    }

    @Transactional
    public List<TestQuestion> saveUniqueQuestions(List<ParsedTestQuestionDto> questions) {
        List<TestQuestion> savedQuestions = new ArrayList<>();

        for (ParsedTestQuestionDto dto : questions) {
            TestQuestion entity = testQuestionMapper.toEntity(dto);
            String hash = entity.getQuestionHash();

            Set<String> compatibleHashes = new LinkedHashSet<>();
            compatibleHashes.add(hash);
            if (dto.getLegacyNormalizedText() != null
                    && dto.getLegacyNormalizedCorrectAnswer() != null) {
                compatibleHashes.add(TestQuestionMapperUtils.generateLegacyHash(
                        dto.getLegacyNormalizedText(),
                        dto.getLegacyAnswers() == null ? dto.getAnswers() : dto.getLegacyAnswers(),
                        dto.getLegacyNormalizedCorrectAnswer()));
            }

            if (!repository.existsByQuestionHashIn(compatibleHashes)) {
                TestQuestion savedQuestion = repository.save(entity);
                savedQuestions.add(savedQuestion);
                log.debug("Saved new question: [hash={}] {}", hash, entity.getQuestionText());
                
                // Notify listeners about new question
                List<String> answers = jsonConverter.getAnswersFromJson(entity);
                for (TestQuestionListener listener : listeners) {
                    try {
                        listener.onNewQuestion(savedQuestion, answers);
                    } catch (RuntimeException e) {
                        // A UI listener must not roll back an otherwise valid
                        // database import (for example in headless operation).
                        log.warn("Question listener failed after saving hash {}: {}",
                                hash, e.getMessage());
                    }
                }
            } else {
                repository.findFirstByQuestionHashIn(compatibleHashes)
                        .ifPresent(existing -> enrichMissingProvenance(existing, entity));
                log.debug("Skipped duplicate question: [hash={}] {}", hash, entity.getQuestionText());
            }
        }

        int newQuestionsCount = savedQuestions.size();
        log.info("Saved {} new questions out of {} total questions processed", 
                newQuestionsCount, questions.size());
        
        parsingStatistics.incrementNewQuestions(newQuestionsCount);
        
        return savedQuestions;
    }

    private void enrichMissingProvenance(TestQuestion existing, TestQuestion parsed) {
        boolean changed = false;

        if (existing.getSourceQuizId() == null && parsed.getSourceQuizId() != null) {
            existing.setSourceQuizId(parsed.getSourceQuizId());
            changed = true;
        }
        if (isBlank(existing.getSourceUrl()) && !isBlank(parsed.getSourceUrl())) {
            existing.setSourceUrl(parsed.getSourceUrl());
            changed = true;
        }
        if (isBlank(existing.getSourceLanguage()) && !isBlank(parsed.getSourceLanguage())) {
            existing.setSourceLanguage(parsed.getSourceLanguage());
            changed = true;
        }
        if (existing.getQuestionType() == null && parsed.getQuestionType() != null) {
            existing.setQuestionType(parsed.getQuestionType());
            changed = true;
        }

        if (changed) {
            repository.save(existing);
            log.debug("Added missing source metadata to duplicate question: [hash={}]",
                    existing.getQuestionHash());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public List<TestQuestion> searchQuestions(String searchText) {
        return repository.searchByAnyFragment(searchText);
    }

    public List<TestQuestion> getAllQuestions() {
        return repository.findAll();
    }

    @SuppressWarnings("unused")
    public Optional<TestQuestion> getQuestionById(Long id) {
        return repository.findById(id);
    }
}
