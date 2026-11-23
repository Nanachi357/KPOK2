package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.QuestionRecordDto;
import com.myprojects.kpok2.service.QuestionAssetService;
import com.myprojects.kpok2.service.TestQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParserController {
    private final TestQuestionService questionService;
    private final QuestionAssetService questionAssetService;

    @GetMapping("/questions")
    public ResponseEntity<List<QuestionRecordDto>> getAllQuestions() {
        return ResponseEntity.ok(questionService.getAllQuestionRecords());
    }

    @GetMapping("/questions/search")
    public ResponseEntity<List<QuestionRecordDto>> searchQuestions(@RequestParam String query) {
        return ResponseEntity.ok(questionService.searchQuestionRecords(query));
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<byte[]> getQuestionAsset(@PathVariable Long id) {
        return questionAssetService.findById(id)
                .map(asset -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(asset.getMediaType()))
                        .contentLength(asset.getContentLength())
                        .eTag(asset.getContentHash())
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365))
                                .cachePublic()
                                .immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header(
                                "Content-Security-Policy",
                                "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.inline()
                                        .filename(
                                                asset.getOriginalFileName(),
                                                StandardCharsets.UTF_8)
                                        .build()
                                        .toString())
                        .body(asset.getContent()))
                .orElseGet(() -> ResponseEntity.<byte[]>notFound().build());
    }
}
