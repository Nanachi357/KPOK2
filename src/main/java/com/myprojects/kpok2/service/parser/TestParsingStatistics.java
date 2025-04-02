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
        this.accountsUsed = new ConcurrentHashMap<>();
        this.sessionHistory = new ArrayList<>();
        this.lastResetTime = LocalDateTime.now();
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
        log.info("Progress: processed={}, success={}, failed={}, newQuestions={}",
                processedCount.get(),
                successCount.get(),
                failedCount.get(),
                newQuestionsCount.get()
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
        private final Map<String, Integer> accountsUsed;
        private final LocalDateTime since;
    }
}