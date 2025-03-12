package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.model.dto.TestParsingResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TestParsingExecutor {
    private final Executor taskExecutor;
    private final TestPageParser pageParser;
    private final TestParsingStatistics statistics;
    private final ConcurrentLinkedQueue<TestParsingResultDto> failedQueue;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public TestParsingExecutor(
            Executor taskExecutor,
            TestPageParser pageParser,
            TestParsingStatistics statistics
    ) {
        this.taskExecutor = taskExecutor;
        this.pageParser = pageParser;
        this.statistics = statistics;
        this.failedQueue = new ConcurrentLinkedQueue<>();
    }

    public List<TestParsingResultDto> executeInBatches(List<String> urls) {
        return urls.stream()
                .map(url -> CompletableFuture.supplyAsync(
                        () -> parseTestWithRetry(url, 1),
                        taskExecutor
                ))
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private TestParsingResultDto parseTestWithRetry(String url, int attempt) {
        try {
            pageParser.parse(url);
            
            return TestParsingResultDto.builder()
                    .testUrl(url)
                    .status(ParsingStatus.SUCCESS)
                    .questions(List.of())
                    .attemptCount(attempt)
                    .build();
        } catch (Exception e) {
            if (attempt < MAX_RETRY_ATTEMPTS) {
                log.warn("Retry {} for {}", attempt, url);
                return parseTestWithRetry(url, attempt + 1);
            }
            throw e;
        }
    }

    public void processFailedQueue() {
        while (!failedQueue.isEmpty()) {
            TestParsingResultDto failed = failedQueue.poll();
            if (failed != null && failed.getAttemptCount() < MAX_RETRY_ATTEMPTS) {
                try {
                    TestParsingResultDto retry = parseTestWithRetry(
                            failed.getTestUrl(),
                            failed.getAttemptCount() + 1
                    );
                    statistics.updateStats(retry);
                } catch (Exception e) {
                    log.error("Final retry failed for {}", failed.getTestUrl());
                }
            }
        }
    }
}