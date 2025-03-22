package com.myprojects.kpok2.controller;

import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MainWindowController {
    
    private final MessageSource messageSource;
    
    @Value("${ui.window.width}")
    private double windowWidth;
    
    @Value("${ui.window.height}")
    private double windowHeight;
    
    public MainWindowController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
    
    @FXML
    public void initialize() {
        // Will be called by JavaFX after FXML is loaded
    }
    
    @FXML
    public void onSettingsClick() {
        // TODO: Open settings window
    }
    
    @FXML
    public void onExitClick() {
        // TODO: Implement proper shutdown
    }
    
    @FXML
    public void onCheckUpdatesClick() {
        // TODO: Implement update check
    }
    
    @FXML
    public void onAboutClick() {
        // TODO: Show about dialog
    }
    
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, Locale.getDefault());
    }
} 