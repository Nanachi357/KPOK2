package com.myprojects.kpok2.service.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestParser {
    private final TestPageParser pageParser;
    private final TestParsingExecutor parsingExecutor;
    private final TestParsingStatistics statistics;

    public void parseTest(String url) {
        parseTests(List.of(url));
    }

    public List<TestParsingResultDto> parseTests(List<String> testUrls) {
        try {
            List<TestParsingResultDto> results = parsingExecutor.executeInBatches(testUrls);
            parsingExecutor.processFailedQueue();
            return results;
        } finally {
            parsingExecutor.shutdown();
        }
    }
}