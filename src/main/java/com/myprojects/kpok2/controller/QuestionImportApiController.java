package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.importing.QuestionImportPreview;
import com.myprojects.kpok2.service.QuestionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only inspection endpoint for question import documents. */
@RestController
@RequestMapping("/api/imports/questions")
@RequiredArgsConstructor
public class QuestionImportApiController {

    private final QuestionImportService importService;

    @PostMapping(
            value = "/preview",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public QuestionImportPreview preview(
            @RequestBody QuestionExportDocument document) {
        return importService.inspect(document);
    }
}
