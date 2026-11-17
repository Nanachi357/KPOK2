package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import com.myprojects.kpok2.service.QuestionImportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit mutation endpoint for atomically importing a validated document. */
@RestController
@RequestMapping("/api/imports/questions")
@RequiredArgsConstructor
public class QuestionImportApplyApiController {

    private final QuestionImportPersistenceService persistenceService;

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public QuestionImportResult apply(
            @RequestBody QuestionExportDocument document) {
        return persistenceService.importDocument(document);
    }
}
