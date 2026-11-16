package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapper;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.QuestionRecordDto;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestQuestionService {
    private final TestQuestionRepository repository;
    private final TestQuestionMapper testQuestionMapper;
    private final TestParsingStatistics parsingStatistics;
    private final QuestionOriginService questionOriginService;
    private final QuestionVersionService questionVersionService;
    
    private final List<TestQuestionListener> listeners = new CopyOnWriteArrayList<>();
    
    public interface TestQuestionListener {
        void onNewQuestion(QuestionRecordDto question);
    }
    
    public void addListener(TestQuestionListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(TestQuestionListener listener) {
        listeners.remove(listener);
    }

    @Transactional
    public synchronized SaveResult saveQuestions(
            List<ParsedTestQuestionDto> questions) {
        List<TestQuestion> savedQuestions = new ArrayList<>();
        int newQuestionRecords = 0;
        int newRevisions = 0;
        int newAnswerKeys = 0;

        for (ParsedTestQuestionDto dto : questions) {
            TestQuestion entity = testQuestionMapper.toEntity(dto);
            String hash = entity.getQuestionHash();

            Optional<TestQuestion> existing = repository.findByQuestionHash(hash);
            boolean newQuestion = existing.isEmpty();
            TestQuestion storedQuestion;
            if (newQuestion) {
                storedQuestion = repository.save(entity);
                savedQuestions.add(storedQuestion);
                log.debug("Saved new canonical question: [hash={}] {}",
                        hash, entity.getQuestionText());
            } else {
                storedQuestion = existing.orElseThrow();
                enrichMissingProvenance(storedQuestion, entity);
                log.debug("Skipped duplicate question: [hash={}] {}", hash, entity.getQuestionText());
            }

            questionOriginService.recordOrigin(storedQuestion, entity);
            QuestionVersionService.VersioningResult versioning =
                    questionVersionService.recordVersion(storedQuestion, dto);

            if (versioning.newRevision()) {
                newRevisions++;
            }
            if (versioning.newAnswerKey()) {
                newAnswerKeys++;
            }
            if (newQuestion || versioning.newRevision() || versioning.newAnswerKey()) {
                newQuestionRecords++;
                notifyListeners(
                        questionVersionService.toRecord(storedQuestion, dto, versioning),
                        hash);
            }
        }

        log.info(
                "Saved {} new question records ({} canonical, {} revisions, {} keys) "
                        + "out of {} questions processed",
                newQuestionRecords,
                savedQuestions.size(),
                newRevisions,
                newAnswerKeys,
                questions.size());
        
        parsingStatistics.incrementNewQuestions(newQuestionRecords);
        
        return new SaveResult(
                savedQuestions,
                newQuestionRecords,
                newRevisions,
                newAnswerKeys);
    }

    private void notifyListeners(QuestionRecordDto question, String hash) {
        for (TestQuestionListener listener : listeners) {
            try {
                listener.onNewQuestion(question);
            } catch (RuntimeException e) {
                // A UI listener must not roll back an otherwise valid database import.
                log.warn("Question listener failed after saving hash {}: {}",
                        hash, e.getMessage());
            }
        }
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
        if (isBlank(existing.getContextText()) && !isBlank(parsed.getContextText())) {
            existing.setContextText(parsed.getContextText());
            existing.setNormalizedContext(parsed.getNormalizedContext());
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

    public List<QuestionRecordDto> getAllQuestionRecords() {
        return questionVersionService.getAllRecords();
    }

    public List<QuestionRecordDto> searchQuestionRecords(String searchText) {
        return questionVersionService.searchRecords(searchText);
    }

    @SuppressWarnings("unused")
    public Optional<TestQuestion> getQuestionById(Long id) {
        return repository.findById(id);
    }

    public record SaveResult(
            List<TestQuestion> newCanonicalQuestions,
            int newQuestionRecords,
            int newRevisions,
            int newAnswerKeys) {

        public SaveResult {
            newCanonicalQuestions = newCanonicalQuestions == null
                    ? List.of()
                    : List.copyOf(newCanonicalQuestions);
        }
    }
}
