package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import com.myprojects.kpok2.service.mapper.TestQuestionMapper;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestQuestionService {
    private final TestQuestionRepository repository;
    private final TestQuestionMapper testQuestionMapper;
    private final TestParsingStatistics parsingStatistics;

    @Transactional
    public List<TestQuestion> saveUniqueQuestions(List<ParsedTestQuestionDto> questions) {
        List<TestQuestion> savedQuestions = new ArrayList<>();

        for (ParsedTestQuestionDto dto : questions) {
            TestQuestion entity = testQuestionMapper.toEntity(dto);
            String hash = entity.getQuestionHash();

            if (!repository.existsByQuestionHash(hash)) {
                savedQuestions.add(repository.save(entity));
                log.debug("Saved new question: [hash={}] {}", hash, entity.getQuestionText());
            } else {
                log.debug("Skipped duplicate question: [hash={}] {}", hash, entity.getQuestionText());
            }
        }

        int newQuestionsCount = savedQuestions.size();
        log.info("Saved {} new questions out of {} total questions processed", 
                newQuestionsCount, questions.size());
        
        // Update parsing statistics with information about new questions
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