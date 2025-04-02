package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.AccountConfigurationService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
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
    
    @FXML
    private CheckBox reuseSessionCheckbox;
    
    @FXML
    private Button saveButton;
    
    @FXML
    private Button cancelButton;
    
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
        try {
            log.info("Initializing navigation config window");
            
            // Load current values from service
            int currentMaxThreads = accountService.getMaxThreads();
            int currentIterationCount = accountService.getIterationCount();
            boolean currentReuseSession = accountService.isReuseSession();
            
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
    
    @FXML
    public void onSaveClick() {
        try {
            // Get values from spinners
            int maxThreads = maxThreadsSpinner.getValue();
            int iterationCount = iterationCountSpinner.getValue();
            boolean reuseSession = reuseSessionCheckbox.isSelected();
            
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
            accountService.setReuseSession(reuseSession);
            
            log.info("Navigation settings saved: maxThreads={}, iterationCount={}, reuseSession={}", 
                     maxThreads, iterationCount, reuseSession);
            
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