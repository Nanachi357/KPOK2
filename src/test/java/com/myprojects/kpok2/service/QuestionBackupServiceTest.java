package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.model.dto.importing.QuestionImportPreview;
import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBackupServiceTest {

    @TempDir
    Path temporaryDirectory;

    private final QuestionExportService exportService =
            mock(QuestionExportService.class);
    private final QuestionImportService importService =
            mock(QuestionImportService.class);
    private final QuestionImportPersistenceService persistenceService =
            mock(QuestionImportPersistenceService.class);
    private final QuestionBackupService service = new QuestionBackupService(
            exportService,
            importService,
            persistenceService);

    @Test
    void createsAnUnfilteredBackupWithAttemptHistory() throws Exception {
        Path target = temporaryDirectory.resolve("backup.json");
        QuestionExportDocument.ExportSummary summary = summary();
        when(exportService.writeJson(target, QuestionExportFilter.all()))
                .thenReturn(summary);

        assertSame(summary, service.createBackup(target));
        verify(exportService).writeJson(target, QuestionExportFilter.all());
    }

    @Test
    void acceptsAValidatedFullBackup() throws Exception {
        Path source = temporaryDirectory.resolve("backup.json");
        QuestionImportService.Inspection inspection = inspection(false);
        when(importService.read(source)).thenReturn(inspection);

        assertSame(inspection, service.inspectBackup(source));
    }

    @Test
    void rejectsAFilteredExportAsABackup() throws Exception {
        Path source = temporaryDirectory.resolve("filtered.json");
        when(importService.read(source)).thenReturn(inspection(true));

        assertThrows(
                QuestionImportValidationException.class,
                () -> service.inspectBackup(source));
        verify(persistenceService, never()).importDocument(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void verifiesTheDocumentFilterInsteadOfTrustingThePreviewFlag() {
        QuestionExportDocument valid = QuestionImportServiceTest.validDocument();
        QuestionExportDocument filteredDocument = new QuestionExportDocument(
                valid.schemaVersion(),
                Instant.now(),
                new QuestionExportDocument.ExportFilter(
                        List.of(173L),
                        List.of(),
                        List.of(),
                        false,
                        true,
                        ""),
                valid.summary(),
                valid.questions(),
                valid.attempts());
        QuestionImportPreview misleadingPreview = new QuestionImportPreview(
                filteredDocument.schemaVersion(),
                filteredDocument.generatedAt(),
                filteredDocument.summary(),
                false,
                List.of());
        QuestionImportService.Inspection inspection =
                new QuestionImportService.Inspection(
                        filteredDocument,
                        misleadingPreview);

        assertThrows(
                QuestionImportValidationException.class,
                () -> service.restoreBackup(inspection));
        verify(persistenceService, never()).importDocument(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restoresTheAlreadyValidatedDocument() {
        QuestionImportService.Inspection inspection = inspection(false);
        QuestionImportResult result = mock(QuestionImportResult.class);
        when(persistenceService.importDocument(inspection.document()))
                .thenReturn(result);

        assertSame(result, service.restoreBackup(inspection));
        verify(persistenceService).importDocument(inspection.document());
    }

    private QuestionImportService.Inspection inspection(boolean filtered) {
        QuestionExportDocument document = QuestionImportServiceTest.validDocument();
        QuestionImportPreview preview = new QuestionImportPreview(
                document.schemaVersion(),
                document.generatedAt(),
                document.summary(),
                filtered,
                List.of());
        return new QuestionImportService.Inspection(document, preview);
    }

    private QuestionExportDocument.ExportSummary summary() {
        return new QuestionExportDocument.ExportSummary(1, 1, 1, 0, 1, 1, 0, 1);
    }
}
