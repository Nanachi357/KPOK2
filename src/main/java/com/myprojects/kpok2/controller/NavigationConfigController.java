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
    
    @FXML
    private Spinner<Integer> iterationCountSpinner;
    
    private final AccountConfigurationService accountService;
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
        
        // Configure iteration count spinner with value constraints
        SpinnerValueFactory<Integer> iterationCountValueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_ITERATIONS, MAX_ITERATIONS, DEFAULT_ITERATIONS);
        iterationCountSpinner.setValueFactory(iterationCountValueFactory);
        iterationCountSpinner.getValueFactory().setValue(accountService.getIterationCount());
        
        // Make spinners editable
        maxThreadsSpinner.setEditable(true);
        iterationCountSpinner.setEditable(true);
        
        // Add listener to enforce constraints for maxThreadsSpinner when edited directly
        maxThreadsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue < MIN_THREADS) {
                maxThreadsSpinner.getValueFactory().setValue(MIN_THREADS);
            } else if (newValue > MAX_THREADS) {
                maxThreadsSpinner.getValueFactory().setValue(MAX_THREADS);
            }
        });
        
        // Add listener to enforce constraints for iterationCountSpinner when edited directly
        iterationCountSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue < MIN_ITERATIONS) {
                iterationCountSpinner.getValueFactory().setValue(MIN_ITERATIONS);
            } else if (newValue > MAX_ITERATIONS) {
                iterationCountSpinner.getValueFactory().setValue(MAX_ITERATIONS);
            }
        });
    }
    
    @FXML
    public void onSaveClick() {
        try {
            // Get values from spinners
            int maxThreads = maxThreadsSpinner.getValue();
            int iterationCount = iterationCountSpinner.getValue();
            
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
            accountService.setMaxThreads(maxThreads);
            accountService.setIterationCount(iterationCount);
            
            log.info("Navigation settings saved: maxThreads={}, iterationCount={}", 
                     maxThreads, iterationCount);
            
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