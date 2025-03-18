package com.myprojects.kpok2.runner;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.service.navigation.*;
import com.myprojects.kpok2.service.parser.TestParsingRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runner for testing the navigation functionality.
 * This class is executed as part of the main application when included in the component scan.
 */
@Slf4j
@Component
public class NavigationTestRunner implements CommandLineRunner {

    @Autowired
    private NavigationSessionFactory sessionFactory;
    
    @Autowired
    private TestCenterNavigator navigator;
    
    @Autowired
    private TestCenterProperties properties;
    
    @Autowired
    private TestParsingRunner testParsingRunner;

    @Override
    public void run(String... args) throws Exception {
        log.info("Navigation test is running...");
        
        try {
            // Create a test navigation service
            NavigationService navigationService = new NavigationService(
                properties, 
                navigator, 
                sessionFactory,
                testParsingRunner
            );
            
            // Start navigation process
            log.info("Starting navigation process...");
            boolean success = navigationService.startNavigation();
            
            if (success) {
                log.info("Navigation process started successfully. Browsers should now be visible.");
                log.info("The program will keep running until you stop it manually.");
                log.info("To stop, click the red square button in IntelliJ IDEA or press Ctrl+C in the console.");
            } else {
                log.error("Failed to start navigation process!");
            }
            
            // Keep the application running until user terminates it
            // This allows the browser windows to stay open
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Error in navigation test: {}", e.getMessage(), e);
        }
    }
} 