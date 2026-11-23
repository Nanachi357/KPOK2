package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.importing.QuestionImportPreview;
import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import com.myprojects.kpok2.service.QuestionBackupService;
import com.myprojects.kpok2.service.QuestionImportService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Creates and restores complete, portable question-data backups. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionBackupDialogController {

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final QuestionBackupService backupService;

    @FXML
    private Button createButton;

    @FXML
    private Button browseButton;

    @FXML
    private Button restoreButton;

    @FXML
    private Button closeButton;

    @FXML
    private Label selectedFileLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextArea previewArea;

    private Stage stage;
    private Path selectedFile;
    private QuestionImportService.Inspection inspection;
    private boolean running;

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            if (running) {
                event.consume();
            }
        });
    }

    @FXML
    public void initialize() {
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        selectedFileLabel.setText("No backup selected");
        statusLabel.setText("Ready to create or restore a full data backup");
        updateButtons();
    }

    @FXML
    public void onCreateClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Create KPOK2 Backup");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("KPOK2 JSON Backup", "*.json"));
        chooser.setInitialFileName("kpok2_backup_"
                + LocalDateTime.now().format(BACKUP_TIMESTAMP)
                + ".json");
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }

        setRunning(true, "Creating backup…");
        Task<QuestionExportDocument.ExportSummary> task = new Task<>() {
            @Override
            protected QuestionExportDocument.ExportSummary call() throws Exception {
                return backupService.createBackup(target.toPath());
            }
        };
        task.setOnSucceeded(event -> {
            QuestionExportDocument.ExportSummary summary = task.getValue();
            previewArea.setText(formatCreatedBackup(target.toPath(), summary));
            setRunning(false, "Backup created successfully");
            log.info("Created full KPOK2 backup at {}", target.getAbsolutePath());
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            setRunning(false, "Backup creation failed");
            log.error("Could not create KPOK2 backup at {}", target, error);
            showError(
                    "Could not create backup",
                    error == null ? "Unknown backup error" : error.getMessage());
        });
        start(task, "question-backup-create");
    }

    @FXML
    public void onBrowseClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose KPOK2 Backup");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("KPOK2 JSON Backup", "*.json"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        selectedFile = selected.toPath();
        inspection = null;
        selectedFileLabel.setText(selected.getAbsolutePath());
        previewArea.clear();
        inspectBackup(selectedFile);
    }

    private void inspectBackup(Path source) {
        setRunning(true, "Validating backup…");
        Task<QuestionImportService.Inspection> task = new Task<>() {
            @Override
            protected QuestionImportService.Inspection call() throws Exception {
                return backupService.inspectBackup(source);
            }
        };
        task.setOnSucceeded(event -> {
            inspection = task.getValue();
            previewArea.setText(formatPreview(inspection.preview()));
            setRunning(false, "Backup is valid; review it before restoring");
        });
        task.setOnFailed(event -> {
            inspection = null;
            Throwable error = task.getException();
            previewArea.clear();
            setRunning(false, "Backup validation failed");
            log.warn("Backup validation failed for {}", source, error);
            showError(
                    "Could not validate backup",
                    error == null ? "Unknown validation error" : error.getMessage());
        });
        start(task, "question-backup-preview");
    }

    @FXML
    public void onRestoreClick() {
        if (inspection == null || running) {
            return;
        }
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Merge this backup into the local question database?\n\n"
                        + "Existing records will be matched, not duplicated. "
                        + "Accounts and passwords are not changed.",
                ButtonType.OK,
                ButtonType.CANCEL);
        confirmation.setTitle("Confirm Backup Restore");
        confirmation.setHeaderText("Restore "
                + inspection.preview().contents().canonicalQuestions()
                + " questions?");
        if (stage != null) {
            confirmation.initOwner(stage);
        }
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        setRunning(true, "Restoring backup…");
        Task<QuestionImportResult> task = new Task<>() {
            @Override
            protected QuestionImportResult call() {
                return backupService.restoreBackup(inspection);
            }
        };
        task.setOnSucceeded(event -> {
            previewArea.setText(formatResult(task.getValue()));
            inspection = null;
            setRunning(false, "Backup restored successfully");
            log.info("Restored KPOK2 backup from {}", selectedFile);
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            setRunning(false, "Restore failed; no partial changes were committed");
            log.error("Could not restore KPOK2 backup from {}", selectedFile, error);
            showError(
                    "Could not restore backup",
                    error == null ? "Unknown restore error" : error.getMessage());
        });
        start(task, "question-backup-restore");
    }

    @FXML
    public void onCloseClick() {
        if (!running && stage != null) {
            stage.close();
        }
    }

    private String formatCreatedBackup(
            Path target,
            QuestionExportDocument.ExportSummary summary) {
        return "Backup file\n" + target.toAbsolutePath().normalize() + "\n\n"
                + formatSummary(summary);
    }

    private String formatPreview(QuestionImportPreview preview) {
        StringBuilder text = new StringBuilder()
                .append("Schema: ").append(preview.schemaVersion()).append('\n')
                .append("Created: ").append(preview.generatedAt()).append("\n\n")
                .append(formatSummary(preview.contents()));
        if (!preview.warnings().isEmpty()) {
            text.append("\n\nNotes:");
            preview.warnings().forEach(warning -> text
                    .append("\n• ")
                    .append(warning));
        }
        return text.toString();
    }

    private String formatSummary(QuestionExportDocument.ExportSummary summary) {
        return "Questions: " + summary.canonicalQuestions() + '\n'
                + "Revisions: " + summary.revisions() + '\n'
                + "Answer keys: " + summary.answerKeys() + '\n'
                + "Origins: " + summary.origins() + '\n'
                + "Observations: " + summary.observations() + '\n'
                + "Collection attempts: " + summary.attempts();
    }

    private String formatResult(QuestionImportResult result) {
        return "Restore result\n\n"
                + "Questions: " + result.canonicalQuestionsCreated()
                + " created, " + result.canonicalQuestionsMerged() + " matched\n"
                + "Revisions: " + result.revisionsCreated()
                + " created, " + result.revisionsMerged() + " matched\n"
                + "Answer keys: " + result.answerKeysCreated()
                + " created, " + result.answerKeysMerged() + " matched\n"
                + "Origins: " + result.originsCreated()
                + " created, " + result.originsMerged() + " matched\n"
                + "Collection attempts: " + result.attemptsCreated()
                + " created, " + result.attemptsMerged() + " matched";
    }

    private void setRunning(boolean value, String status) {
        running = value;
        statusLabel.setText(status);
        updateButtons();
    }

    private void updateButtons() {
        if (createButton == null) {
            return;
        }
        createButton.setDisable(running);
        browseButton.setDisable(running);
        restoreButton.setDisable(running || inspection == null);
        closeButton.setDisable(running);
    }

    private void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (stage != null) {
            alert.initOwner(stage);
        }
        alert.showAndWait();
    }
}
