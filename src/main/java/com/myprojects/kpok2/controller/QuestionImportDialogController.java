package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.importing.QuestionImportPreview;
import com.myprojects.kpok2.model.dto.importing.QuestionImportResult;
import com.myprojects.kpok2.service.QuestionImportPersistenceService;
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

/** Preview-first dialog for safely importing a versioned question JSON file. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionImportDialogController {

    private final QuestionImportService importService;
    private final QuestionImportPersistenceService persistenceService;

    @FXML
    private Label selectedFileLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextArea previewArea;

    @FXML
    private Button browseButton;

    @FXML
    private Button importButton;

    @FXML
    private Button cancelButton;

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
        selectedFileLabel.setText("No file selected");
        statusLabel.setText("Choose a KPOK2 JSON export to inspect");
        updateButtons();
    }

    @FXML
    public void onBrowseClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose KPOK2 Question Export");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        selectedFile = selected.toPath();
        inspection = null;
        selectedFileLabel.setText(selected.getAbsolutePath());
        previewArea.clear();
        loadPreview(selectedFile);
    }

    private void loadPreview(Path source) {
        setRunning(true, "Validating import…");
        Task<QuestionImportService.Inspection> task = new Task<>() {
            @Override
            protected QuestionImportService.Inspection call() throws Exception {
                return importService.read(source);
            }
        };
        task.setOnSucceeded(event -> {
            inspection = task.getValue();
            previewArea.setText(formatPreview(inspection.preview()));
            setRunning(false, "Validation passed; review the summary before importing");
        });
        task.setOnFailed(event -> {
            inspection = null;
            Throwable error = task.getException();
            previewArea.clear();
            setRunning(false, "Validation failed");
            log.warn("Question import validation failed for {}", source, error);
            showError(
                    "Could not validate import",
                    error == null ? "Unknown validation error" : error.getMessage());
        });
        start(task, "question-import-preview");
    }

    @FXML
    public void onImportClick() {
        if (inspection == null || running) {
            return;
        }
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Merge the validated questions into the local database?\n\n"
                        + "Existing records will be matched by their stable hashes.",
                ButtonType.OK,
                ButtonType.CANCEL);
        confirmation.setTitle("Confirm Question Import");
        confirmation.setHeaderText("Import "
                + inspection.preview().contents().canonicalQuestions()
                + " questions?");
        if (stage != null) {
            confirmation.initOwner(stage);
        }
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        setRunning(true, "Importing questions…");
        Task<QuestionImportResult> task = new Task<>() {
            @Override
            protected QuestionImportResult call() {
                return persistenceService.importDocument(inspection.document());
            }
        };
        task.setOnSucceeded(event -> {
            QuestionImportResult result = task.getValue();
            previewArea.setText(formatResult(result));
            setRunning(false, "Import completed successfully");
            inspection = null;
            updateButtons();
            log.info(
                    "Question import completed: {} created, {} matched",
                    result.canonicalQuestionsCreated(),
                    result.canonicalQuestionsMerged());
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            setRunning(false, "Import failed; no partial import was committed");
            log.error("Question import failed for {}", selectedFile, error);
            showError(
                    "Could not import questions",
                    error == null ? "Unknown import error" : error.getMessage());
        });
        start(task, "question-import-apply");
    }

    @FXML
    public void onCancelClick() {
        if (!running && stage != null) {
            stage.close();
        }
    }

    private String formatPreview(QuestionImportPreview preview) {
        var contents = preview.contents();
        StringBuilder text = new StringBuilder()
                .append("Schema: ").append(preview.schemaVersion()).append('\n')
                .append("Generated: ").append(preview.generatedAt()).append("\n\n")
                .append("Questions: ").append(contents.canonicalQuestions()).append('\n')
                .append("Revisions: ").append(contents.revisions()).append('\n')
                .append("Answer keys: ").append(contents.answerKeys()).append('\n')
                .append("Conflicts: ").append(contents.conflicts()).append('\n')
                .append("Origins: ").append(contents.origins()).append('\n')
                .append("Observations: ").append(contents.observations()).append('\n')
                .append("Collection attempts: ").append(contents.attempts());
        if (!preview.warnings().isEmpty()) {
            text.append("\n\nWarnings:");
            preview.warnings().forEach(warning -> text
                    .append("\n• ")
                    .append(warning));
        }
        return text.toString();
    }

    private String formatResult(QuestionImportResult result) {
        return "Import result\n\n"
                + "Questions: " + result.canonicalQuestionsCreated()
                + " created, " + result.canonicalQuestionsMerged() + " matched\n"
                + "Revisions: " + result.revisionsCreated()
                + " created, " + result.revisionsMerged() + " matched\n"
                + "Answer keys: " + result.answerKeysCreated()
                + " created, " + result.answerKeysMerged() + " matched\n"
                + "Origins: " + result.originsCreated()
                + " created, " + result.originsMerged() + " matched\n"
                + "Observations: " + result.observationsCreated()
                + " created, " + result.observationsMerged() + " matched\n"
                + "Collection attempts: " + result.attemptsCreated()
                + " created, " + result.attemptsMerged() + " matched\n"
                + "Image metadata skipped: " + result.assetsSkipped();
    }

    private void setRunning(boolean value, String status) {
        running = value;
        statusLabel.setText(status);
        updateButtons();
    }

    private void updateButtons() {
        if (browseButton == null) {
            return;
        }
        browseButton.setDisable(running);
        cancelButton.setDisable(running);
        importButton.setDisable(running || inspection == null);
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
