package com.myprojects.kpok2.controller;

import ch.qos.logback.classic.Level;
import com.myprojects.kpok2.config.logging.UiLogAppender;
import com.myprojects.kpok2.service.LogConfigService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javafx.application.Platform;
import javafx.scene.control.ToggleGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Component
public class LogConfigController {

    private static final Logger log = LoggerFactory.getLogger(LogConfigController.class);
    private static final String PREF_LOG_LEVEL = "log_level";
    private static final String PREF_AUTOSAVE_ENABLED = "autosave_enabled";
    private static final String PREF_AUTOSAVE_INTERVAL = "autosave_interval";
    private static final String PREF_CLEAR_ON_STARTUP = "clear_logs_on_startup";
    private static final String PREF_AUTOSAVE_NEW_TESTS_ENABLED = "autosave_new_tests_enabled";
    private static final String PREF_AUTOSAVE_NEW_TESTS_INTERVAL = "autosave_new_tests_interval";
    private static final String DEFAULT_LOGS_PATH = "logs";
    
    private final Preferences prefs = Preferences.userNodeForPackage(LogConfigController.class);
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> scheduledTask;
    private static MainWindowController mainWindowController;
    
    @FXML
    private RadioButton debugRadio;
    
    @FXML
    private RadioButton infoRadio;
    
    @FXML
    private RadioButton warnRadio;
    
    @FXML
    private RadioButton errorRadio;
    
    @FXML
    private CheckBox clearLogsOnStartupCheckBox;
    
    @FXML
    private CheckBox autoSaveCheckBox;
    
    @FXML
    private TextField intervalField;
    
    @FXML
    private HBox autoSaveInterval;
    
    @FXML
    private CheckBox autoSaveNewTestsCheckBox;
    
    @FXML
    private TextField newTestsIntervalField;
    
    @FXML
    private HBox newTestsAutoSaveInterval;
    
    private Stage stage;
    
    private final LogConfigService logConfigService;

    public LogConfigController(LogConfigService logConfigService) {
        this.logConfigService = logConfigService;
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    public static void setMainWindowController(MainWindowController controller) {
        mainWindowController = controller;
    }
    
    @FXML
    public void initialize() {
        // Initialize log level radio buttons
        ToggleGroup logLevelGroup = new ToggleGroup();
        debugRadio.setToggleGroup(logLevelGroup);
        infoRadio.setToggleGroup(logLevelGroup);
        warnRadio.setToggleGroup(logLevelGroup);
        errorRadio.setToggleGroup(logLevelGroup);

        // Load current settings
        loadSettings();

        // Add listeners for changes
        autoSaveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoSaveInterval.setDisable(!newVal);
        });
        
        autoSaveNewTestsCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            newTestsAutoSaveInterval.setDisable(!newVal);
        });
        
        // Only allow numbers in interval fields
        intervalField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                intervalField.setText(oldVal);
            }
        });
        
        newTestsIntervalField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                newTestsIntervalField.setText(oldVal);
            }
        });
    }
    
    private void loadSettings() {
        // Load log level
        String currentLevel = logConfigService.getLogLevel();
        switch (currentLevel) {
            case "DEBUG":
                debugRadio.setSelected(true);
                break;
            case "INFO":
                infoRadio.setSelected(true);
                break;
            case "WARN":
                warnRadio.setSelected(true);
                break;
            case "ERROR":
                errorRadio.setSelected(true);
                break;
        }

        // Load clear on startup setting
        clearLogsOnStartupCheckBox.setSelected(logConfigService.isClearOnStartup());

        // Load autosave settings
        autoSaveCheckBox.setSelected(logConfigService.isAutosaveEnabled());
        intervalField.setText(String.valueOf(logConfigService.getAutosaveInterval()));
        autoSaveInterval.setDisable(!autoSaveCheckBox.isSelected());

        // Load new tests autosave settings
        autoSaveNewTestsCheckBox.setSelected(logConfigService.isNewTestsAutosaveEnabled());
        newTestsIntervalField.setText(String.valueOf(logConfigService.getNewTestsAutosaveInterval()));
        newTestsAutoSaveInterval.setDisable(!autoSaveNewTestsCheckBox.isSelected());
    }
    
    @FXML
    public void onSaveClick() {
        try {
            // Save log level
            String logLevel = "INFO"; // Default
            if (debugRadio.isSelected()) logLevel = "DEBUG";
            else if (infoRadio.isSelected()) logLevel = "INFO";
            else if (warnRadio.isSelected()) logLevel = "WARN";
            else if (errorRadio.isSelected()) logLevel = "ERROR";
            logConfigService.setLogLevel(logLevel);

            // Save clear logs on startup setting
            boolean clearOnStartup = clearLogsOnStartupCheckBox.isSelected();
            logConfigService.setClearOnStartup(clearOnStartup);

            // Save autosave settings
            boolean autoSaveEnabled = autoSaveCheckBox.isSelected();
            logConfigService.setAutosaveEnabled(autoSaveEnabled);
            
            int interval = 15;
            try {
                interval = Integer.parseInt(intervalField.getText());
                logConfigService.setAutosaveInterval(interval);
            } catch (NumberFormatException e) {
                log.warn("Invalid interval value, using default: 15");
            }
            
            // Save new tests autosave settings
            boolean autoSaveNewTestsEnabled = autoSaveNewTestsCheckBox.isSelected();
            logConfigService.setNewTestsAutosaveEnabled(autoSaveNewTestsEnabled);
            
            int newTestsInterval = 15;
            try {
                newTestsInterval = Integer.parseInt(newTestsIntervalField.getText());
                logConfigService.setNewTestsAutosaveInterval(newTestsInterval);
            } catch (NumberFormatException e) {
                log.warn("Invalid new tests interval value, using default: 15");
            }
            
            // Configure autosave
            configureAutoSave(autoSaveEnabled, interval);
            
            // Configure new tests autosave
            if (mainWindowController != null) {
                mainWindowController.configureNewTestsAutoSave(autoSaveNewTestsEnabled, newTestsInterval);
            }
            
            log.info("Log settings saved. Level: {}, AutoSave: {}, ClearOnStartup: {}, NewTestsAutoSave: {}", 
                    logLevel, autoSaveEnabled, clearOnStartup, autoSaveNewTestsEnabled);
            
            // Close the dialog
            stage.close();

        } catch (NumberFormatException e) {
            log.error("Invalid interval value", e);
            showError("Error", "Invalid interval value");
        } catch (Exception e) {
            log.error("Error saving settings", e);
            showError("Error", "Failed to save settings");
        }
    }
    
    @FXML
    public void onCancelClick() {
        stage.close();
    }
    
    /**
     * Configure automatic log saving
     * 
     * @param enabled whether autosave is enabled
     * @param intervalMinutes interval in minutes
     */
    private void configureAutoSave(boolean enabled, int intervalMinutes) {
        // Cancel existing scheduled task
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        
        // If not enabled, don't schedule a new task
        if (!enabled) {
            return;
        }
        
        // Create scheduler if it doesn't exist
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        // Create logs directory if it doesn't exist
        File dir = new File(DEFAULT_LOGS_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Schedule periodic log saving
        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (mainWindowController != null) {
                    String timestamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    File logFile = new File(DEFAULT_LOGS_PATH, "app_log_" + timestamp + ".txt");
                    
                    mainWindowController.saveLogsToFile(logFile);
                    log.info("Logs auto-saved to: {}", logFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Error in auto-save: {}", e.getMessage(), e);
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Load settings from preferences and apply them
     */
    public static void applySettingsFromPreferences() {
        Logger logger = LoggerFactory.getLogger(LogConfigController.class);
        logger.info("Applying log settings from preferences");
        
        Preferences prefs = Preferences.userNodeForPackage(LogConfigController.class);
        
        // Apply log level
        String savedLevel = prefs.get(PREF_LOG_LEVEL, "INFO");
        Level level = Level.INFO;
        
        switch (savedLevel) {
            case "DEBUG":
                level = Level.DEBUG;
                break;
            case "WARN":
                level = Level.WARN;
                break;
            case "ERROR":
                level = Level.ERROR;
                break;
            default:
                level = Level.INFO;
        }
        
        logger.info("Setting log level to: {}", level);
        UiLogAppender.setMinimumLevel(level);
        
        // Clear log files if configured
        boolean clearOnStartup = prefs.getBoolean(PREF_CLEAR_ON_STARTUP, false);
        logger.info("Clear logs on startup: {}", clearOnStartup);
        
        if (clearOnStartup) {
            logger.info("Clearing log files...");
            clearLogFiles();
        }
        
        // Setup autosave if enabled
        boolean autoSaveEnabled = prefs.getBoolean(PREF_AUTOSAVE_ENABLED, false);
        logger.info("Auto save enabled: {}", autoSaveEnabled);
        
        if (autoSaveEnabled && mainWindowController != null) {
            int interval = prefs.getInt(PREF_AUTOSAVE_INTERVAL, 15);
            logger.info("Auto save interval: {} minutes", interval);
            
            // Create scheduler
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
                logger.info("Created new scheduler for auto save");
            }
            
            // Create logs directory if it doesn't exist
            File dir = new File(DEFAULT_LOGS_PATH);
            if (!dir.exists()) {
                logger.info("Creating logs directory: {}", dir.getAbsolutePath());
                dir.mkdirs();
            }
            
            // Schedule periodic log saving
            logger.info("Scheduling auto save task every {} minutes", interval);
            scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    String timestamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    File logFile = new File(DEFAULT_LOGS_PATH, "app_log_" + timestamp + ".txt");
                    
                    mainWindowController.saveLogsToFile(logFile);
                    logger.info("Logs auto-saved to: {}", logFile.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Error in auto-save: {}", e.getMessage(), e);
                }
            }, interval, interval, TimeUnit.MINUTES);
        }
        
        // Apply new tests autosave settings
        boolean autoSaveNewTestsEnabled = prefs.getBoolean(PREF_AUTOSAVE_NEW_TESTS_ENABLED, false);
        log.info("New tests auto save enabled: {}", autoSaveNewTestsEnabled);
        
        if (autoSaveNewTestsEnabled && mainWindowController != null) {
            int interval = prefs.getInt(PREF_AUTOSAVE_NEW_TESTS_INTERVAL, 15);
            log.info("New tests auto save interval: {} minutes", interval);
            mainWindowController.configureNewTestsAutoSave(true, interval);
        }
        
        logger.info("Log settings applied successfully");
    }
    
    /**
     * Clear all log files in the logs directory
     */
    private static void clearLogFiles() {
        Logger logger = LoggerFactory.getLogger(LogConfigController.class);
        logger.info("Starting log files cleanup");
        
        try {
            // Also clear the UI logs area if available
            if (mainWindowController != null) {
                logger.info("Clearing UI logs area");
                Platform.runLater(() -> {
                    mainWindowController.clearLogs();
                    logger.info("UI logs area cleared");
                });
            }
            
            File logsDir = new File(DEFAULT_LOGS_PATH);
            logger.info("Checking logs directory: {}", logsDir.getAbsolutePath());
            
            if (logsDir.exists() && logsDir.isDirectory()) {
                File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".txt") || name.endsWith(".log"));
                
                if (logFiles != null) {
                    logger.info("Found {} log files to delete", logFiles.length);
                    
                    Arrays.stream(logFiles).forEach(file -> {
                        try {
                            logger.info("Deleting log file: {}", file.getAbsolutePath());
                            Files.delete(file.toPath());
                            logger.info("Successfully deleted log file: {}", file.getName());
                        } catch (Exception e) {
                            logger.warn("Failed to delete log file {}: {}", file.getName(), e.getMessage());
                        }
                    });
                } else {
                    logger.info("No log files found in directory");
                }
            } else {
                logger.info("Logs directory does not exist or is not a directory");
            }
            
            // Also check for the Logback application.log file
            File logbackFile = new File("logs/application.log");
            if (logbackFile.exists()) {
                try {
                    logger.info("Clearing Logback application.log file");
                    new FileOutputStream(logbackFile).close(); // Truncate the file
                    logger.info("Successfully cleared application.log");
                } catch (Exception e) {
                    logger.warn("Failed to clear application.log: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error clearing log files: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Shutdown the scheduler when application closes
     */
    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (!scheduler.isTerminated()) {
                    scheduler.shutdownNow();
                }
            }
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
} 