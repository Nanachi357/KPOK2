package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.config.TimeoutPreset;
import com.myprojects.kpok2.config.TimeoutSettings;
import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.AccountConfigurationService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Controller for navigation configuration dialog
 */
@Slf4j
@Component
public class NavigationConfigController {

    @FXML
    private ComboBox<TimeoutPreset> timeoutPresetComboBox;
    
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
    private final TimeoutSettings timeoutSettings;
    private final MessageSource messageSource;
    private final TestCenterProperties testCenterProperties;
    private Stage stage;
    
    // Constants for max threads
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 10;
    
    // Constants for iteration count
    private static final int MIN_ITERATIONS = 0;
    private static final int MAX_ITERATIONS = 10000;
    
    @Autowired
    public NavigationConfigController(
            AccountConfigurationService accountService,
            TimeoutSettings timeoutSettings,
            MessageSource messageSource,
            TestCenterProperties testCenterProperties) {
        this.accountService = accountService;
        this.timeoutSettings = timeoutSettings;
        this.messageSource = messageSource;
        this.testCenterProperties = testCenterProperties;
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    @FXML
    public void initialize() {
        try {
            log.info("Initializing navigation config window");
            
            // Setup timeout preset combo box
            setupTimeoutPresets();
            
            // Load current values from services
            int currentMaxThreads = accountService.getMaxThreads();
            int currentIterationCount = accountService.getIterationCount();
            boolean currentReuseSession = accountService.isReuseSession();
            
            log.info("Loaded current settings: maxThreads={}, iterationCount={}, reuseSession={}, timeoutPreset={}", 
                    currentMaxThreads, currentIterationCount, currentReuseSession, timeoutSettings.getCurrentPreset());
            
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
            
            // Select current timeout preset
            timeoutPresetComboBox.setValue(timeoutSettings.getCurrentPreset());
            
        } catch (Exception e) {
            log.error("Error initializing navigation config window", e);
        }
    }
    
    private void setupTimeoutPresets() {
        // Add all presets to combo box
        timeoutPresetComboBox.getItems().addAll(TimeoutPreset.values());
        
        // Set custom cell factory to display localized descriptions
        timeoutPresetComboBox.setCellFactory(param -> new ListCell<TimeoutPreset>() {
            @Override
            protected void updateItem(TimeoutPreset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(messageSource.getMessage("timeout.preset." + item.name().toLowerCase(), 
                            null, item.getDescription(), Locale.getDefault()));
                }
            }
        });
        
        // Set custom button cell to display localized description
        timeoutPresetComboBox.setButtonCell(new ListCell<TimeoutPreset>() {
            @Override
            protected void updateItem(TimeoutPreset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(messageSource.getMessage("timeout.preset." + item.name().toLowerCase(), 
                            null, item.getDescription(), Locale.getDefault()));
                }
            }
        });
    }
    
    @FXML
    public void onSaveClick() {
        try {
            // Get values from UI
            int maxThreads = maxThreadsSpinner.getValue();
            int iterationCount = iterationCountSpinner.getValue();
            boolean reuseSession = reuseSessionCheckbox.isSelected();
            TimeoutPreset selectedPreset = timeoutPresetComboBox.getValue();
            
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
            
            // Save to services
            accountService.setMaxThreads(maxThreads);
            accountService.setIterationCount(iterationCount);
            accountService.setReuseSession(reuseSession);
            timeoutSettings.setPreset(selectedPreset);

            // Update TestCenterProperties navigation settings
            testCenterProperties.getNavigation().setMaxThreads(maxThreads);

            log.info("Navigation settings saved: maxThreads={}, iterationCount={}, reuseSession={}, timeoutPreset={}", 
                     maxThreads, iterationCount, reuseSession, selectedPreset);
            
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