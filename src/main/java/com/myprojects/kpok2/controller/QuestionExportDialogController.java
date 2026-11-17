package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.export.QuestionExportDocument;
import com.myprojects.kpok2.model.dto.export.QuestionExportFilter;
import com.myprojects.kpok2.model.dto.export.QuestionExportOptionsDto;
import com.myprojects.kpok2.service.QuestionExportService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Dialog for selecting JSON export filters and writing the file in the background. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionExportDialogController {

    private final QuestionExportService exportService;

    @FXML
    private ComboBox<SelectionOption<Long>> quizComboBox;

    @FXML
    private ComboBox<SelectionOption<String>> languageComboBox;

    @FXML
    private ComboBox<SelectionOption<QuestionType>> questionTypeComboBox;

    @FXML
    private TextField searchField;

    @FXML
    private CheckBox conflictsOnlyCheckBox;

    @FXML
    private CheckBox includeAttemptsCheckBox;

    @FXML
    private Label statusLabel;

    @FXML
    private Button exportButton;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        includeAttemptsCheckBox.setSelected(true);
        configureComboBox(quizComboBox);
        configureComboBox(languageComboBox);
        configureComboBox(questionTypeComboBox);
        loadOptions();
    }

    private void loadOptions() {
        QuestionExportOptionsDto options = exportService.getOptions();

        List<SelectionOption<Long>> quizzes = new ArrayList<>();
        quizzes.add(new SelectionOption<>(null, "All quizzes"));
        options.quizzes().forEach(quiz -> quizzes.add(new SelectionOption<>(
                quiz.quizId(), quiz.displayName())));
        quizComboBox.setItems(FXCollections.observableArrayList(quizzes));
        quizComboBox.getSelectionModel().selectFirst();

        List<SelectionOption<String>> languages = new ArrayList<>();
        languages.add(new SelectionOption<>(null, "All languages"));
        options.languages().forEach(language -> languages.add(new SelectionOption<>(
                language, language.toUpperCase(Locale.ROOT))));
        languageComboBox.setItems(FXCollections.observableArrayList(languages));
        languageComboBox.getSelectionModel().selectFirst();

        List<SelectionOption<QuestionType>> types = new ArrayList<>();
        types.add(new SelectionOption<>(null, "All question types"));
        options.questionTypes().forEach(type -> types.add(new SelectionOption<>(
                type, type.name().replace('_', ' '))));
        questionTypeComboBox.setItems(FXCollections.observableArrayList(types));
        questionTypeComboBox.getSelectionModel().selectFirst();

        statusLabel.setText(options.quizzes().isEmpty()
                ? "No collected questions are available"
                : "Choose filters and export destination");
        exportButton.setDisable(options.quizzes().isEmpty());
    }

    private <T> void configureComboBox(ComboBox<SelectionOption<T>> comboBox) {
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SelectionOption<T> option) {
                return option == null ? "" : option.label();
            }

            @Override
            public SelectionOption<T> fromString(String value) {
                return null;
            }
        });
        comboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SelectionOption<T> option, boolean empty) {
                super.updateItem(option, empty);
                setText(empty || option == null ? null : option.label());
            }
        });
    }

    @FXML
    public void onExportClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Questions as JSON");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        chooser.setInitialFileName("kpok2_questions_"
                + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                + ".json");
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }

        QuestionExportFilter filter = selectedFilter();
        exportButton.setDisable(true);
        statusLabel.setText("Exporting…");
        Task<QuestionExportDocument.ExportSummary> task = new Task<>() {
            @Override
            protected QuestionExportDocument.ExportSummary call() throws Exception {
                return exportService.writeJson(target.toPath(), filter);
            }
        };
        task.setOnSucceeded(event -> {
            QuestionExportDocument.ExportSummary summary = task.getValue();
            statusLabel.setText("Exported " + summary.canonicalQuestions()
                    + " questions · " + summary.revisions() + " revisions");
            exportButton.setDisable(false);
            log.info("Exported {} questions to {}",
                    summary.canonicalQuestions(), target.getAbsolutePath());
        });
        task.setOnFailed(event -> {
            exportButton.setDisable(false);
            Throwable error = task.getException();
            statusLabel.setText("Export failed");
            log.error("Question JSON export failed", error);
            showError(
                    "Could not export questions",
                    error == null ? "Unknown export error" : error.getMessage());
        });
        Thread thread = new Thread(task, "question-json-export");
        thread.setDaemon(true);
        thread.start();
    }

    private QuestionExportFilter selectedFilter() {
        Long quizId = selectedValue(quizComboBox);
        String language = selectedValue(languageComboBox);
        QuestionType questionType = selectedValue(questionTypeComboBox);
        return new QuestionExportFilter(
                quizId == null ? Set.of() : Set.of(quizId),
                language == null ? Set.of() : Set.of(language),
                questionType == null ? Set.of() : Set.of(questionType),
                conflictsOnlyCheckBox.isSelected(),
                includeAttemptsCheckBox.isSelected(),
                searchField.getText());
    }

    private <T> T selectedValue(ComboBox<SelectionOption<T>> comboBox) {
        SelectionOption<T> selected = comboBox.getValue();
        return selected == null ? null : selected.value();
    }

    @FXML
    public void onCancelClick() {
        stage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record SelectionOption<T>(T value, String label) {
    }
}
