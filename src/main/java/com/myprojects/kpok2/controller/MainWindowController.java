package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.navigation.NavigationManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MainWindowController {
    
    private final MessageSource messageSource;
    private final NavigationManager navigationManager;
    
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
    
    public MainWindowController(MessageSource messageSource, NavigationManager navigationManager) {
        this.messageSource = messageSource;
        this.navigationManager = navigationManager;
    }
    
    @FXML
    public void initialize() {
        // Initial setup
        updateStatus();
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
        // TODO: Open settings window
    }
    
    @FXML
    public void onExitClick() {
        Platform.exit();
    }
    
    @FXML
    public void onCheckUpdatesClick() {
        // TODO: Implement update check
    }
    
    @FXML
    public void onAboutClick() {
        // TODO: Show about dialog
    }
    
    public void appendLog(String message) {
        Platform.runLater(() -> {
            logsArea.appendText(message + "\n");
            logsArea.setScrollTop(Double.MAX_VALUE); // Прокрутка до нових повідомлень
        });
    }
    
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, Locale.getDefault());
    }
} 