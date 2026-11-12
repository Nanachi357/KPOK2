package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.AccountConfigurationService.AccountDTO;
import com.myprojects.kpok2.model.QuizCatalogEntry;
import com.myprojects.kpok2.model.QuizCatalogSnapshot;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.service.catalog.QuizCatalogService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Controller for navigation configuration dialog
 */
@Slf4j
@Component
public class NavigationConfigController {

    @FXML
    private TextField quizUrlField;

    @FXML
    private ComboBox<AccountDTO> catalogAccountComboBox;

    @FXML
    private TextField catalogSearchField;

    @FXML
    private ComboBox<QuizCatalogEntry> quizCatalogComboBox;

    @FXML
    private Button refreshCatalogButton;

    @FXML
    private Label catalogStatusLabel;

    @FXML
    private Spinner<Integer> maxThreadsSpinner;
    
    @FXML
    private Spinner<Integer> iterationCountSpinner;
    
    @FXML
    private CheckBox reuseSessionCheckbox;
    
    @FXML
    private Button saveButton;
    
    @FXML
    private Button cancelButton;
    
    private final AccountConfigurationService accountService;
    private final QuizCatalogService catalogService;
    private final ObservableList<QuizCatalogEntry> catalogEntries = FXCollections.observableArrayList();
    private final FilteredList<QuizCatalogEntry> filteredCatalog =
            new FilteredList<>(catalogEntries, entry -> true);
    private Stage stage;
    
    // Constants for max threads
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 10;
    private static final int DEFAULT_THREADS = 2;
    
    // Constants for iteration count
    private static final int MIN_ITERATIONS = 0;
    private static final int MAX_ITERATIONS = 10000;
    private static final int DEFAULT_ITERATIONS = 10;
    
    @Autowired
    public NavigationConfigController(
            AccountConfigurationService accountService,
            QuizCatalogService catalogService) {
        this.accountService = accountService;
        this.catalogService = catalogService;
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    @FXML
    public void initialize() {
        try {
            log.info("Initializing navigation config window");
            
            // Load current values from service
            int currentMaxThreads = accountService.getMaxThreads();
            int currentIterationCount = accountService.getIterationCount();
            boolean currentReuseSession = accountService.isReuseSession();
            String currentQuizUrl = accountService.getQuizUrl();
            
            log.info("Loaded current settings: maxThreads={}, iterationCount={}, reuseSession={}", 
                    currentMaxThreads, currentIterationCount, currentReuseSession);
            
            // Set up max threads spinner
            SpinnerValueFactory<Integer> maxThreadsFactory = 
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_THREADS, MAX_THREADS, currentMaxThreads);
            maxThreadsSpinner.setValueFactory(maxThreadsFactory);
            
            // Set up iteration count spinner
            SpinnerValueFactory<Integer> iterationCountFactory = 
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_ITERATIONS, MAX_ITERATIONS, currentIterationCount);
            iterationCountSpinner.setValueFactory(iterationCountFactory);
            
            // Set up reuse session checkbox
            reuseSessionCheckbox.setSelected(currentReuseSession);
            quizUrlField.setText(currentQuizUrl);

            configureCatalogControls();
            applyCatalogSnapshot(catalogService.loadCachedCatalog(), true);
            
            // Add tooltip explaining the option
            reuseSessionCheckbox.setTooltip(new Tooltip(
                    "When enabled, browser sessions will be reused between iterations.\n" +
                    "This improves performance but may be less stable.\n" +
                    "When disabled, a new browser session is created for each iteration."
            ));
            
        } catch (Exception e) {
            log.error("Error initializing navigation config window", e);
        }
    }

    private void configureCatalogControls() {
        var enabledAccounts = accountService.getAccounts().stream()
                .filter(AccountDTO::isEnabled)
                .toList();
        catalogAccountComboBox.setItems(FXCollections.observableArrayList(enabledAccounts));
        catalogAccountComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(AccountDTO account) {
                return account == null ? "" : account.getUsername();
            }

            @Override
            public AccountDTO fromString(String value) {
                return null;
            }
        });
        if (!enabledAccounts.isEmpty()) {
            catalogAccountComboBox.getSelectionModel().selectFirst();
        }

        quizCatalogComboBox.setItems(filteredCatalog);
        quizCatalogComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(QuizCatalogEntry entry) {
                return entry == null ? "" : entry.displayName();
            }

            @Override
            public QuizCatalogEntry fromString(String value) {
                return null;
            }
        });
        quizCatalogComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(QuizCatalogEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null ? null : entry.displayName());
            }
        });
        quizCatalogComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                quizUrlField.setText(newValue.url());
            }
        });

        catalogSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue == null
                    ? ""
                    : newValue.trim().toLowerCase(Locale.ROOT);
            filteredCatalog.setPredicate(entry -> query.isEmpty()
                    || entry.searchableText().contains(query));
        });
    }

    @FXML
    public void onRefreshCatalogClick() {
        AccountDTO account = catalogAccountComboBox.getValue();
        if (account == null) {
            showError("No enabled account", "Add or enable a TestCenter account first.");
            return;
        }

        refreshCatalogButton.setDisable(true);
        catalogStatusLabel.setText("Signing in and loading the catalog…");

        Task<QuizCatalogSnapshot> task = new Task<>() {
            @Override
            protected QuizCatalogSnapshot call() {
                return catalogService.refreshCatalog(account);
            }
        };

        task.setOnSucceeded(event -> {
            applyCatalogSnapshot(task.getValue(), false);
            refreshCatalogButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            refreshCatalogButton.setDisable(false);
            String message = task.getException() == null
                    ? "Unknown catalog refresh error"
                    : task.getException().getMessage();
            catalogStatusLabel.setText("Catalog refresh failed");
            showError("Could not refresh the catalog", message);
        });

        Thread thread = new Thread(task, "testcenter-catalog-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyCatalogSnapshot(QuizCatalogSnapshot snapshot, boolean cached) {
        catalogEntries.setAll(snapshot.entries());
        if (snapshot.entries().isEmpty()) {
            catalogStatusLabel.setText("No cached catalog. Use Refresh catalog.");
        } else {
            String source = cached ? "Cached" : "Updated";
            catalogStatusLabel.setText(source + " catalog: " + snapshot.entries().size()
                    + " quizzes" + (snapshot.updatedAt() == null ? "" : " · " + snapshot.updatedAt()));
        }
        selectConfiguredQuiz();
    }

    private void selectConfiguredQuiz() {
        String configuredUrl = quizUrlField.getText();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return;
        }
        catalogEntries.stream()
                .filter(entry -> entry.url().equals(configuredUrl.trim()))
                .findFirst()
                .ifPresent(entry -> quizCatalogComboBox.getSelectionModel().select(entry));
    }
    
    @FXML
    public void onSaveClick() {
        try {
            // Get values from spinners
            int maxThreads = maxThreadsSpinner.getValue();
            int iterationCount = iterationCountSpinner.getValue();
            boolean reuseSession = reuseSessionCheckbox.isSelected();
            String quizUrl = quizUrlField.getText();

            // Parse before saving so an incomplete or unrelated URL cannot
            // start a navigation run against the wrong page.
            QuizTarget.fromUrl(quizUrl);
            
            // Validate max threads
            if (maxThreads < MIN_THREADS) {
                maxThreads = MIN_THREADS;
            } else if (maxThreads > MAX_THREADS) {
                maxThreads = MAX_THREADS;
            }
            
            // Validate iteration count
            if (iterationCount < MIN_ITERATIONS) {
                iterationCount = MIN_ITERATIONS;
            } else if (iterationCount > MAX_ITERATIONS) {
                iterationCount = MAX_ITERATIONS;
            }
            
            // Save to service
            accountService.updateNavigationSettings(quizUrl, maxThreads, iterationCount, reuseSession);
            
            log.info("Navigation settings saved: maxThreads={}, iterationCount={}, reuseSession={}", 
                     maxThreads, iterationCount, reuseSession);
            
            // Close the dialog
            stage.close();
        } catch (Exception e) {
            log.error("Error saving navigation settings", e);
            showError("Invalid navigation settings", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    public void onCancelClick() {
        stage.close();
    }
}
