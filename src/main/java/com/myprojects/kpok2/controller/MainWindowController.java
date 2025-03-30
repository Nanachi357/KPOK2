package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.config.logging.UiLogAppender;
import com.myprojects.kpok2.service.navigation.NavigationManager;
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

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

@Component
public class MainWindowController {
    
    private static final Logger log = LoggerFactory.getLogger(MainWindowController.class);
    
    private final MessageSource messageSource;
    private final NavigationManager navigationManager;
    private final ApplicationContext applicationContext;
    
    @FXML
    private Button startStopButton;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private TextArea logsArea;
    
    @FXML
    private Label statusBarLabel;
    
    @Value("${ui.window.width}")
    private double windowWidth;
    
    @Value("${ui.window.height}")
    private double windowHeight;
    
    public MainWindowController(MessageSource messageSource, NavigationManager navigationManager, ApplicationContext applicationContext) {
        this.messageSource = messageSource;
        this.navigationManager = navigationManager;
        this.applicationContext = applicationContext;
    }
    
    @FXML
    public void initialize() {
        // Register controller with UiLogAppender
        UiLogAppender.setMainWindowController(this);
        
        // Register with LogConfigController
        LogConfigController.setMainWindowController(this);
        
        // Initial setup
        updateStatus();
        
        // Apply log settings from preferences
        LogConfigController.applySettingsFromPreferences();
        
        // Log application startup
        log.info("Application started");
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
    
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, Locale.getDefault());
    }
} 