package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.AccountConfigurationService;
import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Controller for navigation configuration dialog
 */
@Slf4j
@Component
public class NavigationConfigController {

    @FXML
    private Spinner<Integer> maxThreadsSpinner;
    
    private final AccountConfigurationService accountService;
    private Stage stage;
    
    // Constants for max threads
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 10;
    private static final int DEFAULT_THREADS = 2;
    
    @Autowired
    public NavigationConfigController(AccountConfigurationService accountService) {
        this.accountService = accountService;
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    @FXML
    public void initialize() {
        // Configure max threads spinner with value constraints
        SpinnerValueFactory<Integer> maxThreadsValueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_THREADS, MAX_THREADS, DEFAULT_THREADS);
        maxThreadsSpinner.setValueFactory(maxThreadsValueFactory);
        maxThreadsSpinner.getValueFactory().setValue(accountService.getMaxThreads());
        
        // Make spinner editable
        maxThreadsSpinner.setEditable(true);
        
        // Add listener to enforce constraints for maxThreadsSpinner when edited directly
        maxThreadsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue < MIN_THREADS) {
                maxThreadsSpinner.getValueFactory().setValue(MIN_THREADS);
            } else if (newValue > MAX_THREADS) {
                maxThreadsSpinner.getValueFactory().setValue(MAX_THREADS);
            }
        });
    }
    
    @FXML
    public void onSaveClick() {
        try {
            // Get values from spinners
            int maxThreads = maxThreadsSpinner.getValue();
            
            // Validate max threads
            if (maxThreads < MIN_THREADS) {
                maxThreads = MIN_THREADS;
            } else if (maxThreads > MAX_THREADS) {
                maxThreads = MAX_THREADS;
            }
            
            // Save to service
            accountService.setMaxThreads(maxThreads);
            
            log.info("Navigation settings saved: maxThreads={}", maxThreads);
            
            // Close the dialog
            stage.close();
        } catch (Exception e) {
            log.error("Error saving navigation settings", e);
        }
    }
    
    @FXML
    public void onCancelClick() {
        stage.close();
    }
} 