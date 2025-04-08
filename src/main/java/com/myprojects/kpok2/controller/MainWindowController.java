package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.config.logging.UiLogAppender;
import com.myprojects.kpok2.service.navigation.NavigationManager;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.stream.Collectors;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import com.myprojects.kpok2.service.TestQuestionService;
import com.myprojects.kpok2.model.TestQuestion;
import java.util.concurrent.ScheduledFuture;
import java.util.prefs.Preferences;
import java.util.Arrays;
import java.nio.file.Files;
import com.myprojects.kpok2.util.JsonConverter;
import javafx.scene.control.CheckBox;

@Component
public class MainWindowController implements TestQuestionService.TestQuestionListener {
    
    private static final Logger log = LoggerFactory.getLogger(MainWindowController.class);
    
    private final MessageSource messageSource;
    private final NavigationManager navigationManager;
    private final ApplicationContext applicationContext;
    private final TestParsingStatistics parsingStatistics;
    
    private ScheduledExecutorService uiUpdateScheduler;
    private static ScheduledExecutorService scheduler;
    private static MainWindowController mainWindowController;
    
    @Autowired
    private TestQuestionService testQuestionService;
    
    @Autowired
    private JsonConverter jsonConverter;
    
    @FXML
    private Button startStopButton;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private Label iterationLabel;
    
    @FXML
    private TextArea logsArea;
    
    @FXML
    private TextArea newTestsArea;
    
    @FXML
    private Label statusBarLabel;
    
    @Value("${ui.window.width}")
    private double windowWidth;
    
    @Value("${ui.window.height}")
    private double windowHeight;
    
    private static final String PREF_AUTOSAVE_NEW_TESTS_ENABLED = "autosave_new_tests_enabled";
    private static final String PREF_AUTOSAVE_NEW_TESTS_INTERVAL = "autosave_new_tests_interval";
    private static ScheduledFuture<?> newTestsScheduledTask;
    
    @FXML
    private CheckBox numberTestsCheckBox;
    
    public MainWindowController(MessageSource messageSource, 
                               NavigationManager navigationManager, 
                               ApplicationContext applicationContext,
                               TestParsingStatistics parsingStatistics) {
        this.messageSource = messageSource;
        this.navigationManager = navigationManager;
        this.applicationContext = applicationContext;
        this.parsingStatistics = parsingStatistics;
        mainWindowController = this;
    }
    
    @PostConstruct
    public void init() {
        testQuestionService.addListener(this);
    }
    
    @PreDestroy
    public void cleanup() {
        testQuestionService.removeListener(this);
    }
    
    @Override
    public void onNewQuestion(TestQuestion question, List<String> answers) {
        appendNewTest(question.getQuestionText() + "\n" +
                     "Answer Options:\n" +
                     answers.stream().map(answer -> "  " + answer).collect(Collectors.joining("\n")) + "\n" +
                     "Correct Answer: " + question.getCorrectAnswer() + "\n" +
                     "---\n");
    }
    
    @FXML
    public void initialize() {
        // Register controller with UiLogAppender
        UiLogAppender.setMainWindowController(this);
        
        // Register with LogConfigController
        LogConfigController.setMainWindowController(this);
        
        // Initial setup
        updateStatus();
        updateIterationCount();
        
        // Start UI update scheduler
        startUiUpdateScheduler();
        
        // Apply log settings from preferences
        LogConfigController.applySettingsFromPreferences();
        
        // Log application startup
        log.info("Application started");
    }
    
    private void startUiUpdateScheduler() {
        if (uiUpdateScheduler != null) {
            uiUpdateScheduler.shutdownNow();
        }
        
        uiUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
        uiUpdateScheduler.scheduleAtFixedRate(this::updateIterationCount, 1, 1, TimeUnit.SECONDS);
    }
    
    private void updateIterationCount() {
        int completed = parsingStatistics.getCompletedIterationsCount();
        int total = parsingStatistics.getTotalIterationsNeeded();
        
        Platform.runLater(() -> {
            if (total <= 0) {
                iterationLabel.setText("Iterations: " + completed + "/∞");
            } else {
                iterationLabel.setText("Iterations: " + completed + "/" + total);
            }
        });
    }
    
    @FXML
    public void onStartStopClick() {
        if (navigationManager.isRunning()) {
            navigationManager.stopNavigation();
        } else {
            navigationManager.startNavigation();
        }
        updateStatus();
    }
    
    private void updateStatus() {
        boolean isRunning = navigationManager.isRunning();
        Platform.runLater(() -> {
            startStopButton.setText(isRunning ? "Stop" : "Start");
            statusLabel.setText("Status: " + (isRunning ? "Running" : "Stopped"));
            statusBarLabel.setText(isRunning ? "Navigation in progress..." : "Ready");
        });
    }
    
    @FXML
    public void onSettingsClick() {
        // Open navigation configuration window
        openNavigationConfigWindow();
    }
    
    /**
     * Open navigation config window
     */
    private void openNavigationConfigWindow() {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/navigation-config.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            loader.setResources(ResourceBundle.getBundle("i18n.messages"));
            
            Parent root = loader.load();
            
            // Create new stage
            Stage stage = new Stage();
            stage.setTitle(messageSource.getMessage("navigation.config.title", null, Locale.getDefault()));
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            
            // Set stage in controller
            NavigationConfigController controller = loader.getController();
            controller.setStage(stage);
            
            // Show dialog
            stage.showAndWait();
            
            log.info("Opened navigation configuration window");
        } catch (Exception e) {
            log.error("Error opening navigation configuration dialog: {}", e.getMessage(), e);
        }
    }
    
    @FXML
    public void onExitClick() {
        // Shutdown log scheduler
        LogConfigController.shutdown();
        
        // Shutdown UI update scheduler
        if (uiUpdateScheduler != null) {
            uiUpdateScheduler.shutdownNow();
            uiUpdateScheduler = null;
        }
        
        Platform.exit();
    }
    
    @FXML
    public void onManageAccountsClick() {
        try {
            // Get account management controller from Spring context
            AccountManagementController controller = applicationContext.getBean(AccountManagementController.class);
            
            // Show account management window
            controller.showAccountManagementWindow();
            
            log.info("Opened account management window");
        } catch (Exception e) {
            log.error("Error opening account management window: {}", e.getMessage(), e);
        }
    }
    
    @FXML
    public void onCheckUpdatesClick() {
        // TODO: Implement update check
    }
    
    @FXML
    public void onAboutClick() {
        // TODO: Show about dialog
    }
    
    @FXML
    public void onStatisticsClick() {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/parsing-statistics.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            loader.setResources(ResourceBundle.getBundle("i18n.messages"));
            
            Parent root = loader.load();
            
            // Create new stage
            Stage stage = new Stage();
            stage.setTitle(messageSource.getMessage("statistics.title", null, Locale.getDefault()));
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(true);
            
            // Set stage in controller
            ParsingStatisticsController controller = loader.getController();
            controller.setStage(stage);
            
            // Show dialog
            stage.showAndWait();
            
            log.info("Opened parsing statistics window");
        } catch (Exception e) {
            log.error("Error opening parsing statistics dialog: {}", e.getMessage(), e);
        }
    }
    
    @FXML
    public void onConfigureLogsClick() {
        try {
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/log-config.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            loader.setResources(ResourceBundle.getBundle("i18n.messages"));
            
            Parent root = loader.load();
            
            // Create new stage
            Stage stage = new Stage();
            stage.setTitle(messageSource.getMessage("logs.config.title", null, Locale.getDefault()));
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            
            // Set stage in controller
            LogConfigController controller = loader.getController();
            controller.setStage(stage);
            
            // Show dialog
            stage.showAndWait();
            
        } catch (Exception e) {
            log.error("Error opening log configuration dialog: {}", e.getMessage(), e);
        }
    }
    
    @FXML
    public void onClearLogsClick() {
        Platform.runLater(() -> {
            logsArea.clear();
            log.info("Logs cleared");
        });
    }
    
    @FXML
    public void onSaveLogsClick() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Logs");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            fileChooser.setInitialFileName("logs_" + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + 
                    ".txt");
            
            File file = fileChooser.showSaveDialog(logsArea.getScene().getWindow());
            
            if (file != null) {
                saveLogsToFile(file);
            }
        } catch (Exception e) {
            log.error("Error saving logs: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save logs to a file
     * 
     * @param file file to save logs to
     * @return true if saved successfully
     */
    public boolean saveLogsToFile(File file) {
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(logsArea.getText());
            log.info("Logs saved to file: {}", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("Error saving logs to file: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Clear logs in the UI text area
     */
    public void clearLogs() {
        Platform.runLater(() -> {
            logsArea.clear();
            log.info("Logs cleared programmatically");
        });
    }
    
    /**
     * Append log message to UI text area.
     * This method is called by UiLogAppender.
     * 
     * @param message log message to append
     */
    public void appendLog(String message) {
        Platform.runLater(() -> {
            logsArea.appendText(message + "\n");
            logsArea.setScrollTop(Double.MAX_VALUE); // Auto-scroll to bottom
        });
    }
    
    @FXML
    public void onClearNewTestsClick() {
        Platform.runLater(() -> {
            newTestsArea.clear();
            log.info("New tests cleared");
        });
    }
    
    @FXML
    public void onSaveNewTestsClick() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save New Tests");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            fileChooser.setInitialFileName("new_tests_" + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + 
                    ".txt");
            
            File file = fileChooser.showSaveDialog(newTestsArea.getScene().getWindow());
            
            if (file != null) {
                saveNewTestsToFile(file);
            }
        } catch (Exception e) {
            log.error("Error saving new tests: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save new tests to a file
     */
    public boolean saveNewTestsToFile(File file) {
        try {
            String content = newTestsArea.getText();
            if (numberTestsCheckBox.isSelected()) {
                // Split content by test separator
                String[] tests = content.split("---\n");
                StringBuilder numberedContent = new StringBuilder();
                int testNumber = 1;
                
                for (String test : tests) {
                    if (!test.trim().isEmpty()) {
                        numberedContent.append(testNumber++).append(". ").append(test.trim()).append("\n---\n");
                    }
                }
                content = numberedContent.toString();
            }
            
            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.write(content);
                log.info("New tests saved to file: {}", file.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            log.error("Error saving new tests to file: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Append new test to UI text area
     */
    public void appendNewTest(String testText) {
        Platform.runLater(() -> {
            newTestsArea.appendText(testText + "\n");
            newTestsArea.setScrollTop(Double.MAX_VALUE); // Auto-scroll to bottom
        });
    }
    
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, Locale.getDefault());
    }
    
    /**
     * Configure automatic saving of new tests
     */
    public void configureNewTestsAutoSave(boolean enabled, int intervalMinutes) {
        // Cancel existing scheduled task
        if (newTestsScheduledTask != null && !newTestsScheduledTask.isDone()) {
            newTestsScheduledTask.cancel(false);
        }
        
        // If not enabled, don't schedule a new task
        if (!enabled) {
            return;
        }
        
        // Create scheduler if it doesn't exist
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        // Create new_tests directory if it doesn't exist
        File dir = new File("new_tests");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Schedule periodic saving
        newTestsScheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                File testFile = new File("new_tests", "new_tests_" + timestamp + ".txt");
                
                saveNewTestsToFile(testFile);
                log.info("New tests auto-saved to: {}", testFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Error in new tests auto-save: {}", e.getMessage(), e);
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Apply new tests auto-save settings from preferences
     */
    public static void applyNewTestsSettingsFromPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(LogConfigController.class);
        
        boolean autoSaveEnabled = prefs.getBoolean(PREF_AUTOSAVE_NEW_TESTS_ENABLED, false);
        log.info("New tests auto save enabled: {}", autoSaveEnabled);
        
        if (autoSaveEnabled && mainWindowController != null) {
            int interval = prefs.getInt(PREF_AUTOSAVE_NEW_TESTS_INTERVAL, 15);
            log.info("New tests auto save interval: {} minutes", interval);
            
            // Create scheduler if needed
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
            }
            
            // Create new_tests directory
            File dir = new File("new_tests");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Schedule periodic saving
            newTestsScheduledTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    String timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    File testFile = new File("new_tests", "new_tests_" + timestamp + ".txt");
                    
                    mainWindowController.saveNewTestsToFile(testFile);
                    log.info("New tests auto-saved to: {}", testFile.getAbsolutePath());
                } catch (Exception e) {
                    log.error("Error in new tests auto-save: {}", e.getMessage(), e);
                }
            }, interval, interval, TimeUnit.MINUTES);
        }
    }
    
    /**
     * Clear new tests files if configured
     */
    private static void clearNewTestsFiles() {
        log.info("Starting new tests files cleanup");
        
        try {
            // Clear the UI new tests area if available
            if (mainWindowController != null) {
                log.info("Clearing UI new tests area");
                Platform.runLater(() -> {
                    mainWindowController.onClearNewTestsClick();
                    log.info("UI new tests area cleared");
                });
            }
            
            File newTestsDir = new File("new_tests");
            log.info("Checking new tests directory: {}", newTestsDir.getAbsolutePath());
            
            if (newTestsDir.exists() && newTestsDir.isDirectory()) {
                File[] testFiles = newTestsDir.listFiles((dir, name) -> name.endsWith(".txt"));
                
                if (testFiles != null) {
                    log.info("Found {} test files to delete", testFiles.length);
                    
                    Arrays.stream(testFiles).forEach(file -> {
                        try {
                            log.info("Deleting test file: {}", file.getAbsolutePath());
                            Files.delete(file.toPath());
                            log.info("Successfully deleted test file: {}", file.getName());
                        } catch (Exception e) {
                            log.warn("Failed to delete test file {}: {}", file.getName(), e.getMessage());
                        }
                    });
                } else {
                    log.info("No test files found in directory");
                }
            } else {
                log.info("New tests directory does not exist or is not a directory");
            }
            
        } catch (Exception e) {
            log.error("Error clearing new tests files: {}", e.getMessage(), e);
        }
    }
    
    @FXML
    public void onExportAllTestsClick() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export All Tests");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            fileChooser.setInitialFileName("all_tests_" + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + 
                    ".txt");
            
            File file = fileChooser.showSaveDialog(newTestsArea.getScene().getWindow());
            
            if (file != null) {
                List<TestQuestion> allQuestions = testQuestionService.getAllQuestions();
                if (allQuestions.isEmpty()) {
                    log.warn("No tests found in the database");
                    return;
                }
                
                StringBuilder content = new StringBuilder();
                boolean shouldNumber = numberTestsCheckBox.isSelected();
                int testNumber = 1;
                
                for (TestQuestion question : allQuestions) {
                    List<String> answers = jsonConverter.getAnswersFromJson(question);
                    if (shouldNumber) {
                        content.append(testNumber++).append(". ");
                    }
                    content.append(question.getQuestionText()).append("\n")
                           .append("Answer Options:\n")
                           .append(answers.stream().map(answer -> "  " + answer).collect(Collectors.joining("\n")))
                           .append("\n")
                           .append("Correct Answer: ").append(question.getCorrectAnswer())
                           .append("\n---\n");
                }
                
                try (FileWriter fileWriter = new FileWriter(file)) {
                    fileWriter.write(content.toString());
                    log.info("All tests exported to file: {}", file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("Error exporting all tests: {}", e.getMessage(), e);
        }
    }
} 