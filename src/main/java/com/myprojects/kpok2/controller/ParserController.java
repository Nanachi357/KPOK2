package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.TestParsingResultDto;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.service.parser.TestParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParserController {
    private final TestParser testParser;
    private final TestQuestionService questionService;

    @PostMapping("/parse")
    public ResponseEntity<Void> parseTest(@RequestParam String url) {
        testParser.parseTest(url);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/parse/results")
    public ResponseEntity<List<TestParsingResultDto>> getParsingResults() {
        List<TestParsingResultDto> results = testParser.getLastParsingResults();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/questions")
    public ResponseEntity<List<TestQuestion>> getAllQuestions() {
        return ResponseEntity.ok(questionService.getAllQuestions());
    }

    @GetMapping("/questions/search")
    public ResponseEntity<List<TestQuestion>> searchQuestions(@RequestParam String query) {
        return ResponseEntity.ok(questionService.searchQuestions(query));
    }
}