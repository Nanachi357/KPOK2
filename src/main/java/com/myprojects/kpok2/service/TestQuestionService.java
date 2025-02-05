package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.repository.TestQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestQuestionService {
    private final TestQuestionRepository testQuestionRepository;

    @Transactional
    public TestQuestion saveQuestion(TestQuestion question) {
        return testQuestionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public List<TestQuestion> getAllQuestions() {
        return testQuestionRepository.findAll();
    }
}