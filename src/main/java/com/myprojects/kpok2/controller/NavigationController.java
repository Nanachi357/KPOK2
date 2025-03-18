package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.navigation.NavigationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PreDestroy;

/**
 * REST controller for managing navigation operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/navigation")
public class NavigationController {

    private final NavigationService navigationService;
    
    @Autowired
    public NavigationController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }
    
    /**
     * Start the navigation process.
     * This will authenticate all accounts and navigate to test pages.
     * 
     * @return Response with the result of the operation
     */
    @PostMapping("/start")
    public ResponseEntity<String> startNavigation() {
        log.info("Received request to start navigation");
        
        try {
            boolean success = navigationService.startNavigation();
            
            if (success) {
                return ResponseEntity.ok("Navigation process started successfully");
            } else {
                return ResponseEntity.internalServerError().body("Failed to start navigation process");
            }
        } catch (Exception e) {
            log.error("Error starting navigation: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error starting navigation: " + e.getMessage());
        }
    }
    
    /**
     * Stop all navigation processes.
     * 
     * @return Response with the result of the operation
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopNavigation() {
        log.info("Received request to stop navigation");
        
        try {
            navigationService.shutdown();
            return ResponseEntity.ok("Navigation processes stopped");
        } catch (Exception e) {
            log.error("Error stopping navigation: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error stopping navigation: " + e.getMessage());
        }
    }
    
    /**
     * Check the status of the navigation system.
     * 
     * @return Response with system status information
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("Navigation system is running");
    }
    
    /**
     * Ensure resources are closed when the application is shut down.
     */
    @PreDestroy
    public void cleanup() {
        log.info("Application shutting down, cleaning up navigation resources");
        navigationService.shutdown();
    }
} 