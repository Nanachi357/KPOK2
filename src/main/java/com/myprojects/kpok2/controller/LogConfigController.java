package com.myprojects.kpok2.controller;

import ch.qos.logback.classic.Level;
import com.myprojects.kpok2.config.logging.UiLogAppender;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javafx.application.Platform;

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
    
    private Stage stage;
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    public static void setMainWindowController(MainWindowController controller) {
        mainWindowController = controller;
    }
    
    @FXML
    public void initialize() {
        // Load saved preferences
        String savedLevel = prefs.get(PREF_LOG_LEVEL, "INFO");
        boolean autoSaveEnabled = prefs.getBoolean(PREF_AUTOSAVE_ENABLED, false);
        boolean clearOnStartup = prefs.getBoolean(PREF_CLEAR_ON_STARTUP, false);
        int interval = prefs.getInt(PREF_AUTOSAVE_INTERVAL, 15);
        
        // Set radio buttons based on saved level
        switch (savedLevel) {
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
            default:
                infoRadio.setSelected(true);
        }
        
        // Set up autosave fields
        clearLogsOnStartupCheckBox.setSelected(clearOnStartup);
        autoSaveCheckBox.setSelected(autoSaveEnabled);
        intervalField.setText(String.valueOf(interval));
        
        // Enable/disable autosave settings based on checkbox
        autoSaveInterval.setDisable(!autoSaveEnabled);
        autoSaveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoSaveInterval.setDisable(!newVal);
        });
        
        // Only allow numbers in interval field
        intervalField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                intervalField.setText(oldVal);
            }
        });
    }
    
    @FXML
    public void onSaveClick() {
        // Determine selected log level
        Level selectedLevel = Level.INFO; // Default
        
        if (debugRadio.isSelected()) {
            selectedLevel = Level.DEBUG;
            prefs.put(PREF_LOG_LEVEL, "DEBUG");
        } else if (infoRadio.isSelected()) {
            selectedLevel = Level.INFO;
            prefs.put(PREF_LOG_LEVEL, "INFO");
        } else if (warnRadio.isSelected()) {
            selectedLevel = Level.WARN;
            prefs.put(PREF_LOG_LEVEL, "WARN");
        } else if (errorRadio.isSelected()) {
            selectedLevel = Level.ERROR;
            prefs.put(PREF_LOG_LEVEL, "ERROR");
        }
        
        // Set log level in the UiLogAppender
        UiLogAppender.setMinimumLevel(selectedLevel);
        
        // Save clear logs on startup setting
        boolean clearOnStartup = clearLogsOnStartupCheckBox.isSelected();
        prefs.putBoolean(PREF_CLEAR_ON_STARTUP, clearOnStartup);
        
        // Save autosave settings
        boolean autoSaveEnabled = autoSaveCheckBox.isSelected();
        prefs.putBoolean(PREF_AUTOSAVE_ENABLED, autoSaveEnabled);
        
        int interval = 15;
        try {
            interval = Integer.parseInt(intervalField.getText());
            prefs.putInt(PREF_AUTOSAVE_INTERVAL, interval);
        } catch (NumberFormatException e) {
            log.warn("Invalid interval value, using default: 15");
        }
        
        // Configure autosave
        configureAutoSave(autoSaveEnabled, interval);
        
        log.info("Log settings saved. Level: {}, AutoSave: {}, ClearOnStartup: {}", 
                selectedLevel, autoSaveEnabled, clearOnStartup);
        
        // Close the dialog
        stage.close();
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
} 