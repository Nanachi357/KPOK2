package com.myprojects.kpok2.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.myprojects.kpok2.controller.MainWindowController;

/**
 * Logback appender that redirects logs to JavaFX UI
 */
public class UiLogAppender extends AppenderBase<ILoggingEvent> {
    
    private static MainWindowController mainWindowController;
    private static Level minimumLevel = Level.INFO; // Default to INFO level
    
    /**
     * Set the MainWindowController instance for log redirection
     * 
     * @param controller the MainWindowController instance
     */
    public static void setMainWindowController(MainWindowController controller) {
        mainWindowController = controller;
    }
    
    /**
     * Set the minimum level of logs to display in UI
     * 
     * @param level the minimum log level to display
     */
    public static void setMinimumLevel(Level level) {
        minimumLevel = level;
    }
    
    /**
     * Get the current minimum log level
     * 
     * @return current minimum log level
     */
    public static Level getMinimumLevel() {
        return minimumLevel;
    }
    
    @Override
    protected void append(ILoggingEvent event) {
        // Skip events below minimum level
        if (event.getLevel().toInt() < minimumLevel.toInt()) {
            return;
        }
        
        if (mainWindowController != null) {
            String formattedMessage = formatLogMessage(event);
            mainWindowController.appendLog(formattedMessage);
        }
    }
    
    /**
     * Format the log message with level, logger name, and message
     * 
     * @param event the logging event
     * @return formatted log message
     */
    private String formatLogMessage(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        
        // Add log level
        sb.append('[').append(event.getLevel()).append(']');
        
        // Add logger name (simplified)
        String loggerName = event.getLoggerName();
        if (loggerName != null && !loggerName.isEmpty()) {
            String simpleName = loggerName.substring(loggerName.lastIndexOf('.') + 1);
            sb.append(' ').append(simpleName).append(": ");
        } else {
            sb.append(' ');
        }
        
        // Add message
        sb.append(event.getFormattedMessage());
        
        return sb.toString();
    }
} 