package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.TestParsingResultDto;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class TestParsingStatistics {
    private final AtomicInteger processedCount;
    private final AtomicInteger successCount;
    private final AtomicInteger failedCount;
    private final AtomicInteger newQuestionsCount;
    private final AtomicInteger completedIterationsCount;
    private final AtomicInteger totalIterationsNeeded;
    
    // Map account username to number of pages parsed by that account
    private final Map<String, Integer> accountsUsed;
    
    // Store individual parsing session details
    private final List<ParsingSessionInfo> sessionHistory;
    
    // Last reset time
    private LocalDateTime lastResetTime;
    
    public TestParsingStatistics() {
        this.processedCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
        this.newQuestionsCount = new AtomicInteger(0);
        this.completedIterationsCount = new AtomicInteger(0);
        this.totalIterationsNeeded = new AtomicInteger(0);
        this.accountsUsed = new ConcurrentHashMap<>();
        this.sessionHistory = new ArrayList<>();
        this.lastResetTime = LocalDateTime.now();
    }

    /**
     * Set the total number of iterations needed
     * @param count the total number of iterations to perform
     */
    public void setTotalIterationsNeeded(int count) {
        totalIterationsNeeded.set(count);
        if (count <= 0) {
            log.info("Iteration mode set to: unlimited");
        } else {
            log.info("Total iterations needed set to: {}", count);
        }
    }
    
    /**
     * Increment the count of completed iterations
     * @return the new count of completed iterations
     */
    public int incrementCompletedIterations() {
        int newValue = completedIterationsCount.incrementAndGet();
        int total = totalIterationsNeeded.get();
        
        if (total <= 0) {
            log.info("Completed iteration {} (unlimited mode)", newValue);
        } else {
            log.info("Completed iteration {}/{} ({}%)", 
                     newValue, total, 
                     Math.round((newValue * 100.0) / total));
        }
        
        return newValue;
    }
    
    /**
     * Check if more iterations are needed
     * @return true if more iterations should be performed, false otherwise
     */
    public boolean isMoreIterationsNeeded() {
        // If totalIterationsNeeded is 0, it means unlimited iterations
        if (totalIterationsNeeded.get() <= 0) {
            return true;
        }
        
        boolean result = completedIterationsCount.get() < totalIterationsNeeded.get();
        
        if (!result) {
            log.info("Target iteration count reached: {}/{}", 
                     completedIterationsCount.get(), 
                     totalIterationsNeeded.get());
        }
        
        return result;
    }
    
    /**
     * Get the count of completed iterations
     * @return the current count of completed iterations
     */
    public int getCompletedIterationsCount() {
        return completedIterationsCount.get();
    }
    
    /**
     * Get the total iterations needed
     * @return the total number of iterations that need to be performed
     */
    public int getTotalIterationsNeeded() {
        return totalIterationsNeeded.get();
    }

    public void updateStats(TestParsingResultDto result) {
        processedCount.incrementAndGet();
        if (result.getStatus() == ParsingStatus.SUCCESS) {
            successCount.incrementAndGet();
        } else {
            failedCount.incrementAndGet();
        }
    }
    
    /**
     * Update statistics with information about new questions saved
     */
    public void incrementNewQuestions(int count) {
        if (count > 0) {
            newQuestionsCount.addAndGet(count);
            log.info("Added {} new questions to statistics. Total new questions: {}", 
                    count, newQuestionsCount.get());
        }
    }
    
    /**
     * Register account activity in statistics
     */
    public void registerAccountActivity(String username, int pagesParsed) {
        // Update or set the account usage count
        accountsUsed.compute(username, (key, oldValue) -> 
                (oldValue == null) ? pagesParsed : oldValue + pagesParsed);
        log.info("Account {} parsed {} pages. Total pages for this account: {}", 
                username, pagesParsed, accountsUsed.get(username));
    }
    
    /**
     * Add information about a completed parsing session
     */
    public void addSessionInfo(String accountUsername, int pagesParsed, int newQuestions) {
        ParsingSessionInfo sessionInfo = new ParsingSessionInfo(
                accountUsername, 
                LocalDateTime.now(), 
                pagesParsed, 
                newQuestions
        );
        sessionHistory.add(sessionInfo);
        log.info("Added new parsing session info: {}", sessionInfo);
    }

    public void logProgress() {
        log.info("Progress: processed={}, success={}, failed={}, newQuestions={}, iterations={}/{}",
                processedCount.get(),
                successCount.get(),
                failedCount.get(),
                newQuestionsCount.get(),
                completedIterationsCount.get(),
                totalIterationsNeeded.get() > 0 ? totalIterationsNeeded.get() : "unlimited"
        );
        
        if (!accountsUsed.isEmpty()) {
            log.info("Accounts used: {}", accountsUsed);
        }
    }
    
    /**
     * Get the current statistics data
     */
    public StatisticsData getCurrentStats() {
        return new StatisticsData(
                processedCount.get(),
                successCount.get(),
                failedCount.get(),
                newQuestionsCount.get(),
                completedIterationsCount.get(),
                totalIterationsNeeded.get(),
                new HashMap<>(accountsUsed),
                lastResetTime
        );
    }
    
    /**
     * Get detailed session history
     */
    public List<ParsingSessionInfo> getSessionHistory() {
        return new ArrayList<>(sessionHistory);
    }
    
    /**
     * Reset all statistics data
     */
    public void resetStats() {
        processedCount.set(0);
        successCount.set(0);
        failedCount.set(0);
        newQuestionsCount.set(0);
        completedIterationsCount.set(0);
        // Don't reset totalIterationsNeeded, as we want to keep the setting
        accountsUsed.clear();
        sessionHistory.clear();
        lastResetTime = LocalDateTime.now();
        log.info("Statistics reset at {}", lastResetTime);
    }
    
    /**
     * Class to hold parsing session information
     */
    @Data
    public static class ParsingSessionInfo {
        private final String accountUsername;
        private final LocalDateTime timestamp;
        private final int pagesParsed;
        private final int newQuestionsFound;
    }
    
    /**
     * Class to hold complete statistics data
     */
    @Data
    public static class StatisticsData {
        private final int processedCount;
        private final int successCount;
        private final int failedCount;
        private final int newQuestionsCount;
        private final int completedIterations;
        private final int totalIterationsNeeded;
        private final Map<String, Integer> accountsUsed;
        private final LocalDateTime since;
    }
}