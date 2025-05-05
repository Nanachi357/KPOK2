package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.config.TimeoutConfig;
import com.myprojects.kpok2.config.TimeoutPreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final TimeoutConfig timeoutConfig;

    @Autowired
    public ConfigController(TimeoutConfig timeoutConfig) {
        this.timeoutConfig = timeoutConfig;
    }

    @GetMapping("/timeout/presets")
    public List<TimeoutPresetDTO> getTimeoutPresets() {
        return Arrays.stream(TimeoutPreset.values())
                .map(preset -> new TimeoutPresetDTO(
                    preset.name(),
                    preset.getDescription(),
                    preset.getPageLoadTimeoutSeconds(),
                    preset.getNavigationTimeoutSeconds(),
                    preset.getElementTimeoutSeconds()
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/timeout/current")
    public TimeoutPresetDTO getCurrentPreset() {
        TimeoutPreset current = timeoutConfig.getCurrentPreset();
        return new TimeoutPresetDTO(
            current.name(),
            current.getDescription(),
            current.getPageLoadTimeoutSeconds(),
            current.getNavigationTimeoutSeconds(),
            current.getElementTimeoutSeconds()
        );
    }

    @PostMapping("/timeout/preset/{presetName}")
    public ResponseEntity<?> setTimeoutPreset(@PathVariable String presetName) {
        try {
            TimeoutPreset preset = TimeoutPreset.valueOf(presetName.toUpperCase());
            timeoutConfig.setPreset(preset);
            log.info("Timeout preset changed to: {}", preset.getDescription());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid preset name");
        }
    }

    public static class TimeoutPresetDTO {
        private final String name;
        private final String description;
        private final int pageLoadTimeout;
        private final int navigationTimeout;
        private final int elementTimeout;

        public TimeoutPresetDTO(String name, String description, int pageLoadTimeout, 
                              int navigationTimeout, int elementTimeout) {
            this.name = name;
            this.description = description;
            this.pageLoadTimeout = pageLoadTimeout;
            this.navigationTimeout = navigationTimeout;
            this.elementTimeout = elementTimeout;
        }

        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getPageLoadTimeout() { return pageLoadTimeout; }
        public int getNavigationTimeout() { return navigationTimeout; }
        public int getElementTimeout() { return elementTimeout; }
    }
} 