package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.dto.TestParsingResultDto;
import com.myprojects.kpok2.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestParser {
    private final TestParsingExecutor parsingExecutor;
    private final AuthenticationService authenticationService;
    private final CopyOnWriteArrayList<TestParsingResultDto> lastResults = new CopyOnWriteArrayList<>();

    public void parseTest(String url) {
        parseTests(List.of(url));
    }

    private void parseTests(List<String> testUrls) {
        if (!authenticationService.login()) {
            throw new RuntimeException("Failed to authenticate");
        }
        List<TestParsingResultDto> results = parsingExecutor.executeInBatches(testUrls);
        parsingExecutor.processFailedQueue();
        lastResults.clear();
        lastResults.addAll(results);
    }

    public List<TestParsingResultDto> getLastParsingResults() {
        return new ArrayList<>(lastResults);
    }
}