package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.model.dto.export.QuestionExportOptionsDto;
import com.myprojects.kpok2.service.QuestionExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** REST access to versioned filtered question exports. */
@RestController
@RequestMapping("/api/exports/questions")
@RequiredArgsConstructor
public class QuestionExportApiController {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);

    private final QuestionExportService exportService;

    @GetMapping("/options")
    public QuestionExportOptionsDto getOptions() {
        return exportService.getOptions();
    }

    @GetMapping
    public ResponseEntity<QuestionExportDocument> export(
            @RequestParam(name = "quizId", required = false) Set<Long> quizIds,
            @RequestParam(name = "language", required = false) Set<String> languages,
            @RequestParam(name = "questionType", required = false)
                    Set<QuestionType> questionTypes,
            @RequestParam(defaultValue = "false") boolean conflictsOnly,
            @RequestParam(defaultValue = "true") boolean includeAttempts,
            @RequestParam(defaultValue = "") String query) {
        QuestionExportDocument document = exportService.buildExport(
                new QuestionExportFilter(
                        quizIds,
                        languages,
                        questionTypes,
                        conflictsOnly,
                        includeAttempts,
                        query));
        String fileName = "kpok2-questions-"
                + FILE_TIMESTAMP.format(document.generatedAt()) + ".json";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-KPOK2-Export-Schema", document.schemaVersion())
                .body(document);
    }
}
