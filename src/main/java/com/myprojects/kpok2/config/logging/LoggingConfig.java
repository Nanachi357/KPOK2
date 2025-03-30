package com.myprojects.kpok2.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.myprojects.kpok2.controller.LogConfigController;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import java.util.prefs.Preferences;

/**
 * Configuration for setting up UI logging appender programmatically
 */
@Configuration
public class LoggingConfig {
    
    private static final String PREF_LOG_LEVEL = "log_level";
    private static final String PREF_CLEAR_ON_STARTUP = "clear_logs_on_startup";
    private UiLogAppender uiLogAppender;
    
    /**
     * Configure and attach the UI log appender after Spring context is initialized
     */
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingConfig.class);
        logger.info("Initializing UI log appender");
        
        // Get the Logback logger context
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Get root logger
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        
        // Create and configure the UI log appender
        uiLogAppender = new UiLogAppender();
        uiLogAppender.setContext(loggerContext);
        uiLogAppender.setName("uiLogAppender");
        
        // Set initial log level from preferences if available
        Preferences prefs = Preferences.userNodeForPackage(LogConfigController.class);
        String savedLevel = prefs.get(PREF_LOG_LEVEL, "INFO");
        Level level = Level.INFO;
        
        switch (savedLevel) {
            case "DEBUG":
                level = Level.DEBUG;
                break;
            case "WARN":
                level = Level.WARN;
                break;
            case "ERROR":
                level = Level.ERROR;
                break;
            default:
                level = Level.INFO;
        }
        
        logger.info("Setting initial log level to: {}", level);
        UiLogAppender.setMinimumLevel(level);
        
        // Check if we should clear logs on startup
        boolean clearOnStartup = prefs.getBoolean(PREF_CLEAR_ON_STARTUP, false);
        logger.info("Clear logs on startup (from LoggingConfig): {}", clearOnStartup);
        
        // Start the appender
        uiLogAppender.start();
        
        // Attach the appender to the root logger
        rootLogger.addAppender(uiLogAppender);
        
        logger.info("UI log appender initialized and attached to root logger");
    }
} 