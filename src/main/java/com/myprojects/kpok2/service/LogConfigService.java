package com.myprojects.kpok2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

import java.util.prefs.Preferences;

@Service
public class LogConfigService {
    private static final Logger log = LoggerFactory.getLogger(LogConfigService.class);
    private static final String PREF_LOG_LEVEL = "log_level";
    private static final String PREF_CLEAR_ON_STARTUP = "clear_on_startup";
    private static final String PREF_AUTOSAVE_ENABLED = "autosave_enabled";
    private static final String PREF_AUTOSAVE_INTERVAL = "autosave_interval";
    private static final String PREF_AUTOSAVE_NEW_TESTS_ENABLED = "autosave_new_tests_enabled";
    private static final String PREF_AUTOSAVE_NEW_TESTS_INTERVAL = "autosave_new_tests_interval";

    private final Preferences prefs;
    private final LoggerContext loggerContext;

    public LogConfigService() {
        this.prefs = Preferences.userNodeForPackage(LogConfigService.class);
        this.loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    public String getLogLevel() {
        return prefs.get(PREF_LOG_LEVEL, "INFO");
    }

    public void setLogLevel(String level) {
        prefs.put(PREF_LOG_LEVEL, level);
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.valueOf(level));
        log.info("Log level set to: {}", level);
    }

    public boolean isClearOnStartup() {
        return prefs.getBoolean(PREF_CLEAR_ON_STARTUP, false);
    }

    public void setClearOnStartup(boolean clearOnStartup) {
        prefs.putBoolean(PREF_CLEAR_ON_STARTUP, clearOnStartup);
        log.info("Clear on startup set to: {}", clearOnStartup);
    }

    public boolean isAutosaveEnabled() {
        return prefs.getBoolean(PREF_AUTOSAVE_ENABLED, false);
    }

    public void setAutosaveEnabled(boolean enabled) {
        prefs.putBoolean(PREF_AUTOSAVE_ENABLED, enabled);
        log.info("Log autosave enabled: {}", enabled);
    }

    public int getAutosaveInterval() {
        return prefs.getInt(PREF_AUTOSAVE_INTERVAL, 15);
    }

    public void setAutosaveInterval(int interval) {
        prefs.putInt(PREF_AUTOSAVE_INTERVAL, interval);
        log.info("Log autosave interval set to: {} minutes", interval);
    }

    public boolean isNewTestsAutosaveEnabled() {
        return prefs.getBoolean(PREF_AUTOSAVE_NEW_TESTS_ENABLED, false);
    }

    public void setNewTestsAutosaveEnabled(boolean enabled) {
        prefs.putBoolean(PREF_AUTOSAVE_NEW_TESTS_ENABLED, enabled);
        log.info("New tests autosave enabled: {}", enabled);
    }

    public int getNewTestsAutosaveInterval() {
        return prefs.getInt(PREF_AUTOSAVE_NEW_TESTS_INTERVAL, 15);
    }

    public void setNewTestsAutosaveInterval(int interval) {
        prefs.putInt(PREF_AUTOSAVE_NEW_TESTS_INTERVAL, interval);
        log.info("New tests autosave interval set to: {} minutes", interval);
    }
} 