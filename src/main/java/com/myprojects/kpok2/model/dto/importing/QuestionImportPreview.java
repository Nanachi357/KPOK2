package com.myprojects.kpok2.model.dto.importing;

import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;

import java.time.Instant;
import java.util.List;

/** Read-only description of a validated question export before it is imported. */
public record QuestionImportPreview(
        String schemaVersion,
        Instant generatedAt,
        QuestionExportDocument.ExportSummary contents,
        boolean filteredExport,
        List<String> warnings) {

    public QuestionImportPreview {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
