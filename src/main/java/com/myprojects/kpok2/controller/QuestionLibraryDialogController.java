package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.dto.QuestionLibraryItemDto;
import com.myprojects.kpok2.model.dto.QuestionLibraryPageDto;
import com.myprojects.kpok2.service.QuestionLibraryService;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionLibraryDialogController {

    private static final int PAGE_SIZE = QuestionLibraryService.DEFAULT_PAGE_SIZE;

    private final QuestionLibraryService questionLibraryService;
    private final AtomicLong requestSequence = new AtomicLong();

    @FXML
    private ResourceBundle resources;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<QuestionLibraryItemDto> questionsTable;
    @FXML
    private TableColumn<QuestionLibraryItemDto, Number> idColumn;
    @FXML
    private TableColumn<QuestionLibraryItemDto, String> contextColumn;
    @FXML
    private TableColumn<QuestionLibraryItemDto, String> questionColumn;
    @FXML
    private TableColumn<QuestionLibraryItemDto, String> typeColumn;
    @FXML
    private TableColumn<QuestionLibraryItemDto, Number> revisionsColumn;
    @FXML
    private TableColumn<QuestionLibraryItemDto, Number> keysColumn;
    @FXML
    private TableColumn<QuestionLibraryItemDto, String> conflictColumn;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private Label pageLabel;
    @FXML
    private Label resultLabel;
    @FXML
    private ProgressIndicator loadingIndicator;

    private Stage stage;
    private QuestionLibraryPageDto currentPage;
    private boolean loading;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(data -> new ReadOnlyLongWrapper(
                data.getValue().questionId()));
        contextColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                safe(data.getValue().contextText())));
        questionColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                safe(data.getValue().questionText())));
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                formatType(data.getValue())));
        revisionsColumn.setCellValueFactory(data -> new ReadOnlyIntegerWrapper(
                data.getValue().revisionCount()));
        keysColumn.setCellValueFactory(data -> new ReadOnlyIntegerWrapper(
                data.getValue().answerKeyCount()));
        conflictColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().answerKeyConflict()
                        ? text("question.library.conflict.yes")
                        : "—"));
        configureWrappingCell(contextColumn);
        configureWrappingCell(questionColumn);
        questionsTable.setPlaceholder(new Label(text("question.library.no.results")));

        loadPage(0);
    }

    @FXML
    public void onSearchClick() {
        loadPage(0);
    }

    @FXML
    public void onClearClick() {
        searchField.clear();
        loadPage(0);
    }

    @FXML
    public void onPreviousClick() {
        if (currentPage != null && !currentPage.first()) {
            loadPage(currentPage.page() - 1);
        }
    }

    @FXML
    public void onNextClick() {
        if (currentPage != null && !currentPage.last()) {
            loadPage(currentPage.page() + 1);
        }
    }

    @FXML
    public void onCloseClick() {
        if (stage != null) {
            stage.close();
        }
    }

    private void loadPage(int pageNumber) {
        long requestId = requestSequence.incrementAndGet();
        String query = searchField.getText();
        setLoading(true);
        resultLabel.setText(text("question.library.loading"));

        Task<QuestionLibraryPageDto> task = new Task<>() {
            @Override
            protected QuestionLibraryPageDto call() {
                return questionLibraryService.findPage(query, pageNumber, PAGE_SIZE);
            }
        };
        task.setOnSucceeded(event -> {
            if (requestSequence.get() != requestId) {
                return;
            }
            currentPage = task.getValue();
            questionsTable.getItems().setAll(currentPage.items());
            updatePageSummary();
            setLoading(false);
        });
        task.setOnFailed(event -> {
            if (requestSequence.get() != requestId) {
                return;
            }
            Throwable failure = task.getException();
            log.error("Could not load question-library page", failure);
            resultLabel.setText(format(
                    "question.library.load.failed",
                    failure == null ? "" : safe(failure.getMessage())));
            setLoading(false);
        });

        Thread worker = new Thread(task, "question-library-page-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void updatePageSummary() {
        long total = currentPage.totalItems();
        long start = total == 0 ? 0 : (long) currentPage.page() * currentPage.size() + 1;
        long end = total == 0 ? 0 : start + currentPage.items().size() - 1;
        resultLabel.setText(total == 0
                ? text("question.library.no.results")
                : format("question.library.result.range", start, end, total));
        pageLabel.setText(format(
                "question.library.page",
                total == 0 ? 0 : currentPage.page() + 1,
                currentPage.totalPages()));
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        previousButton.setDisable(
                loading || currentPage == null || currentPage.first());
        nextButton.setDisable(
                loading || currentPage == null || currentPage.last());
    }

    private void configureWrappingCell(
            TableColumn<QuestionLibraryItemDto, String> column) {
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Label label = new Label();

            {
                label.setWrapText(true);
                label.prefWidthProperty().bind(column.widthProperty().subtract(14));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                } else {
                    label.setText(item);
                    setGraphic(label);
                }
                setText(null);
            }
        });
    }

    private String formatType(QuestionLibraryItemDto item) {
        return item.questionType() == null
                ? "—"
                : item.questionType().name()
                        .toLowerCase(Locale.ROOT)
                        .replace('_', ' ');
    }

    private String text(String key) {
        return resources.getString(key);
    }

    private String format(String key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
