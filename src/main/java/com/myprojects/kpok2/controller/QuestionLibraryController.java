package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.QuestionLibraryPageDto;
import com.myprojects.kpok2.service.QuestionLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/question-library")
@RequiredArgsConstructor
public class QuestionLibraryController {
    private final QuestionLibraryService questionLibraryService;

    @GetMapping
    public ResponseEntity<QuestionLibraryPageDto> findQuestions(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(questionLibraryService.findPage(query, page, size));
    }
}
