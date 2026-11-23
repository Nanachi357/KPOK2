package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Creates and restores full portable question-data backups. */
@Service
@RequiredArgsConstructor
public class QuestionBackupService {

    private final QuestionExportService exportService;
    private final QuestionImportService importService;
    private final QuestionImportPersistenceService persistenceService;

    public QuestionExportDocument.ExportSummary createBackup(Path target)
            throws IOException {
        return exportService.writeJson(target, QuestionExportFilter.all());
    }

    public QuestionImportService.Inspection inspectBackup(Path source)
            throws IOException {
        QuestionImportService.Inspection inspection = importService.read(source);
        requireFullBackup(inspection);
        return inspection;
    }

    public QuestionImportResult restoreBackup(
            QuestionImportService.Inspection inspection) {
        requireFullBackup(inspection);
        return persistenceService.importDocument(inspection.document());
    }

    private void requireFullBackup(QuestionImportService.Inspection inspection) {
        if (inspection == null || inspection.document() == null
                || inspection.preview() == null) {
            throw new QuestionImportValidationException(
                    List.of("A validated backup is required"));
        }
        QuestionExportDocument.ExportFilter filter =
                inspection.document().filters();
        if (filter == null
                || hasValues(filter.quizIds())
                || hasValues(filter.languages())
                || hasValues(filter.questionTypes())
                || filter.conflictsOnly()
                || !filter.includeAttempts()
                || filter.query() != null && !filter.query().isBlank()
                || inspection.preview().filteredExport()) {
            throw new QuestionImportValidationException(List.of(
                    "The selected file is a filtered export, not a full backup"));
        }
    }

    private boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }
}
