package com.myprojects.kpok2.service.parser;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class TestParsingExecutor {
    private static final int THREAD_POOL_SIZE = 10;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ExecutorService executorService;
    private final Queue<TestParsingResultDto> failedQueue;
    private final TestPageParser pageParser;
    private final TestParsingStatistics statistics;

    public TestParsingExecutor(TestPageParser pageParser, TestParsingStatistics statistics) {
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.failedQueue = new ConcurrentLinkedQueue<>();
        this.pageParser = pageParser;
        this.statistics = statistics;
    }

    public List<TestParsingResultDto> executeInBatches(List<String> testUrls) {
        List<TestParsingResultDto> results = new ArrayList<>();

        for (List<String> batch : Lists.partition(testUrls, BATCH_SIZE)) {
            List<CompletableFuture<TestParsingResultDto>> futures = batch.stream()
                    .map(this::parseTestAsync)
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();

            futures.stream()
                    .map(CompletableFuture::join)
                    .forEach(result -> {
                        results.add(result);
                        statistics.updateStats(result);
                        if (result.getStatus() == ParsingStatus.FAILED) {
                            failedQueue.offer(result);
                        }
                    });

            statistics.logProgress();
        }

        return results;
    }

    private CompletableFuture<TestParsingResultDto> parseTestAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseTestWithRetry(url, 1);
            } catch (Exception e) {
                log.error("Failed to parse test {}: {}", url, e.getMessage());
                return TestParsingResultDto.builder()
                        .testUrl(url)
                        .status(ParsingStatus.FAILED)
                        .errorMessage(e.getMessage())
                        .build();
            }
        }, executorService);
    }

    private TestParsingResultDto parseTestWithRetry(String url, int attempt) {
        try {
            List<ParsedTestQuestionDto> questions = pageParser.parseAllPages(url);
            return TestParsingResultDto.builder()
                    .testUrl(url)
                    .status(ParsingStatus.SUCCESS)
                    .questions(questions)
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

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}