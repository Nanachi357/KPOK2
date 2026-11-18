package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.model.QuestionType;
import com.myprojects.kpok2.model.dto.QuestionLibraryFilter;
import com.myprojects.kpok2.model.dto.QuestionLibraryDetailDto;
import com.myprojects.kpok2.model.dto.QuestionLibraryItemDto;
import com.myprojects.kpok2.model.dto.QuestionLibraryPageDto;
import com.myprojects.kpok2.model.dto.export.QuestionExportOptionsDto;
import com.myprojects.kpok2.service.QuestionExportService;
import com.myprojects.kpok2.service.QuestionLibraryDetailService;
import com.myprojects.kpok2.service.QuestionLibraryService;
import javafx.collections.FXCollections;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ListCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionLibraryDialogController {

    private static final int PAGE_SIZE = QuestionLibraryService.DEFAULT_PAGE_SIZE;

    private final QuestionLibraryService questionLibraryService;
    private final QuestionExportService questionExportService;
    private final QuestionLibraryDetailService questionLibraryDetailService;
    private final AtomicLong requestSequence = new AtomicLong();
    private final AtomicLong detailRequestSequence = new AtomicLong();

    @FXML
    private ResourceBundle resources;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<SelectionOption<Long>> quizComboBox;
    @FXML
    private ComboBox<SelectionOption<String>> languageComboBox;
    @FXML
    private ComboBox<SelectionOption<QuestionType>> questionTypeComboBox;
    @FXML
    private CheckBox conflictsOnlyCheckBox;
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
    @FXML
    private ProgressIndicator detailLoadingIndicator;
    @FXML
    private TextArea detailArea;

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
        questionsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> onQuestionSelected(selected));
        detailArea.setText(text("question.library.detail.prompt"));
        setDetailLoading(false);

        configureComboBox(quizComboBox);
        configureComboBox(languageComboBox);
        configureComboBox(questionTypeComboBox);
        initializeEmptyFilterOptions();
        loadFilterOptions();
        loadPage(0);
    }

    @FXML
    public void onSearchClick() {
        loadPage(0);
    }

    @FXML
    public void onClearClick() {
        searchField.clear();
        quizComboBox.getSelectionModel().selectFirst();
        languageComboBox.getSelectionModel().selectFirst();
        questionTypeComboBox.getSelectionModel().selectFirst();
        conflictsOnlyCheckBox.setSelected(false);
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
        QuestionLibraryFilter filter = selectedFilter();
        setLoading(true);
        resultLabel.setText(text("question.library.loading"));

        Task<QuestionLibraryPageDto> task = new Task<>() {
            @Override
            protected QuestionLibraryPageDto call() {
                return questionLibraryService.findPage(filter, pageNumber, PAGE_SIZE);
            }
        };
        task.setOnSucceeded(event -> {
            if (requestSequence.get() != requestId) {
                return;
            }
            currentPage = task.getValue();
            clearDetail();
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

    private void onQuestionSelected(QuestionLibraryItemDto selected) {
        if (selected == null) {
            clearDetail();
            return;
        }
        loadDetail(selected.questionId());
    }

    private void loadDetail(Long questionId) {
        long requestId = detailRequestSequence.incrementAndGet();
        detailArea.setText(text("question.library.detail.loading"));
        setDetailLoading(true);

        Task<QuestionLibraryDetailDto> task = new Task<>() {
            @Override
            protected QuestionLibraryDetailDto call() {
                return questionLibraryDetailService.findByQuestionId(questionId)
                        .orElseThrow(() -> new IllegalStateException(
                                text("question.library.detail.not.found")));
            }
        };
        task.setOnSucceeded(event -> {
            if (detailRequestSequence.get() != requestId) {
                return;
            }
            detailArea.setText(formatDetail(task.getValue()));
            detailArea.positionCaret(0);
            setDetailLoading(false);
        });
        task.setOnFailed(event -> {
            if (detailRequestSequence.get() != requestId) {
                return;
            }
            Throwable failure = task.getException();
            log.error("Could not load question-library detail", failure);
            detailArea.setText(format(
                    "question.library.detail.failed",
                    failure == null ? "" : safe(failure.getMessage())));
            setDetailLoading(false);
        });
        Thread worker = new Thread(task, "question-library-detail-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void clearDetail() {
        detailRequestSequence.incrementAndGet();
        setDetailLoading(false);
        detailArea.setText(text("question.library.detail.prompt"));
    }

    private void setDetailLoading(boolean loading) {
        detailLoadingIndicator.setVisible(loading);
        detailLoadingIndicator.setManaged(loading);
    }

    private String formatDetail(QuestionLibraryDetailDto detail) {
        StringBuilder value = new StringBuilder();
        value.append(format("question.library.detail.header", detail.questionId()))
                .append('\n');
        value.append(text("question.library.detail.type"))
                .append(": ")
                .append(formatType(detail.questionType()))
                .append('\n');
        value.append(text("question.library.detail.hash"))
                .append(": ")
                .append(safe(detail.questionHash()))
                .append("\n\n");
        appendTextSection(
                value,
                text("question.library.detail.context"),
                detail.contextText());
        appendTextSection(
                value,
                text("question.library.detail.question"),
                detail.questionText());

        value.append(text("question.library.detail.origins"))
                .append(" (")
                .append(detail.origins().size())
                .append(")\n");
        if (detail.origins().isEmpty()) {
            value.append("  —\n");
        }
        for (QuestionLibraryDetailDto.Origin origin : detail.origins()) {
            value.append("  • ")
                    .append(format(
                            "question.library.detail.origin",
                            origin.quizId(),
                            safe(origin.language()).toUpperCase(Locale.ROOT),
                            formatDate(origin.firstSeenAt()),
                            formatDate(origin.lastSeenAt())))
                    .append('\n');
            if (!safe(origin.quizUrl()).isBlank()) {
                value.append("    ").append(origin.quizUrl()).append('\n');
            }
        }

        value.append('\n')
                .append(text("question.library.detail.revisions"))
                .append(" (")
                .append(detail.revisions().size())
                .append(")\n");
        for (QuestionLibraryDetailDto.Revision revision : detail.revisions()) {
            value.append("\n")
                    .append(format(
                            "question.library.detail.revision",
                            revision.revisionId(),
                            formatType(revision.questionType()),
                            formatDate(revision.firstSeenAt()),
                            formatDate(revision.lastSeenAt())))
                    .append('\n');
            if (revision.answerKeyConflict()) {
                value.append(text("question.library.detail.conflict")).append('\n');
            }
            appendTextSection(
                    value,
                    text("question.library.detail.context"),
                    revision.contextText());
            appendTextSection(
                    value,
                    text("question.library.detail.question"),
                    revision.questionText());
            value.append(text("question.library.detail.answers")).append("\n");
            for (int index = 0; index < revision.answers().size(); index++) {
                value.append("  ")
                        .append(index + 1)
                        .append(". ")
                        .append(revision.answers().get(index))
                        .append('\n');
            }
            value.append(text("question.library.detail.keys")).append("\n");
            for (QuestionLibraryDetailDto.AnswerKey key : revision.answerKeys()) {
                value.append("  • ")
                        .append(format(
                                "question.library.detail.key",
                                key.answerKeyId(),
                                String.join(" | ", key.correctAnswers()),
                                formatDate(key.firstSeenAt()),
                                formatDate(key.lastSeenAt())))
                        .append('\n');
            }
        }
        return value.toString().stripTrailing();
    }

    private void appendTextSection(StringBuilder target, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        target.append(title).append("\n")
                .append(content.strip()).append("\n\n");
    }

    private String formatDate(LocalDateTime value) {
        return value == null
                ? "—"
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(value);
    }

    private void initializeEmptyFilterOptions() {
        quizComboBox.setItems(FXCollections.observableArrayList(
                new SelectionOption<>(null, text("question.library.filter.all.quizzes"))));
        languageComboBox.setItems(FXCollections.observableArrayList(
                new SelectionOption<>(null, text("question.library.filter.all.languages"))));
        questionTypeComboBox.setItems(FXCollections.observableArrayList(
                new SelectionOption<>(null, text("question.library.filter.all.types"))));
        quizComboBox.getSelectionModel().selectFirst();
        languageComboBox.getSelectionModel().selectFirst();
        questionTypeComboBox.getSelectionModel().selectFirst();
    }

    private void loadFilterOptions() {
        Task<QuestionExportOptionsDto> task = new Task<>() {
            @Override
            protected QuestionExportOptionsDto call() {
                return questionExportService.getOptions();
            }
        };
        task.setOnSucceeded(event -> applyFilterOptions(task.getValue()));
        task.setOnFailed(event -> log.warn(
                "Could not load question-library filter options",
                task.getException()));
        Thread worker = new Thread(task, "question-library-filter-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void applyFilterOptions(QuestionExportOptionsDto options) {
        List<SelectionOption<Long>> quizzes = new ArrayList<>();
        quizzes.add(new SelectionOption<>(
                null, text("question.library.filter.all.quizzes")));
        options.quizzes().forEach(quiz -> quizzes.add(new SelectionOption<>(
                quiz.quizId(), quiz.displayName())));
        quizComboBox.setItems(FXCollections.observableArrayList(quizzes));
        quizComboBox.getSelectionModel().selectFirst();

        List<SelectionOption<String>> languages = new ArrayList<>();
        languages.add(new SelectionOption<>(
                null, text("question.library.filter.all.languages")));
        options.languages().forEach(language -> languages.add(new SelectionOption<>(
                language, language.toUpperCase(Locale.ROOT))));
        languageComboBox.setItems(FXCollections.observableArrayList(languages));
        languageComboBox.getSelectionModel().selectFirst();

        List<SelectionOption<QuestionType>> types = new ArrayList<>();
        types.add(new SelectionOption<>(
                null, text("question.library.filter.all.types")));
        options.questionTypes().forEach(type -> types.add(new SelectionOption<>(
                type,
                type.name().toLowerCase(Locale.ROOT).replace('_', ' '))));
        questionTypeComboBox.setItems(FXCollections.observableArrayList(types));
        questionTypeComboBox.getSelectionModel().selectFirst();
    }

    private QuestionLibraryFilter selectedFilter() {
        return new QuestionLibraryFilter(
                searchField.getText(),
                selectedValue(quizComboBox),
                selectedValue(languageComboBox),
                selectedValue(questionTypeComboBox),
                conflictsOnlyCheckBox.isSelected());
    }

    private <T> T selectedValue(ComboBox<SelectionOption<T>> comboBox) {
        SelectionOption<T> selected = comboBox.getValue();
        return selected == null ? null : selected.value();
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
        comboBox.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(SelectionOption<T> option, boolean empty) {
                super.updateItem(option, empty);
                setText(empty || option == null ? null : option.label());
            }
        });
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
        return formatType(item.questionType());
    }

    private String formatType(QuestionType questionType) {
        return questionType == null
                ? "—"
                : questionType.name()
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

    private record SelectionOption<T>(T value, String label) {
    }
}
