package com.myprojects.kpok2.service.parser;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TestParsingStatistics {
    private final AtomicInteger processedCount;
    private final AtomicInteger successCount;
    private final AtomicInteger failedCount;

    public TestParsingStatistics() {
        this.processedCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
    }

    public void updateStats(TestParsingResultDto result) {
        processedCount.incrementAndGet();
        if (result.getStatus() == ParsingStatus.SUCCESS) {
            successCount.incrementAndGet();
        } else {
            failedCount.incrementAndGet();
        }
    }

    public void logProgress() {
        log.info("Progress: processed={}, success={}, failed={}",
                processedCount.get(),
                successCount.get(),
                failedCount.get()
        );
    }
}