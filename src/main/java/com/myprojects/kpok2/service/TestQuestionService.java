package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapper;
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

            if (!repository.existsByQuestionHash(hash)) {
                TestQuestion savedQuestion = repository.save(entity);
                savedQuestions.add(savedQuestion);
                log.debug("Saved new question: [hash={}] {}", hash, entity.getQuestionText());
                
                // Notify listeners about new question
                List<String> answers = jsonConverter.getAnswersFromJson(entity);
                for (TestQuestionListener listener : listeners) {
                    listener.onNewQuestion(savedQuestion, answers);
                }
            } else {
                log.debug("Skipped duplicate question: [hash={}] {}", hash, entity.getQuestionText());
            }
        }

        int newQuestionsCount = savedQuestions.size();
        log.info("Saved {} new questions out of {} total questions processed", 
                newQuestionsCount, questions.size());
        
        parsingStatistics.incrementNewQuestions(newQuestionsCount);
        
        return savedQuestions;
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