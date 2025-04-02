package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.exception.parser.ParsingErrorType;
import com.myprojects.kpok2.exception.parser.ParsingException;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridge class that delegates test page parsing to TestParsingRunner
 * This class exists to maintain backward compatibility with TestParsingExecutor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestPageParser {
    private final TestParsingRunner parsingRunner;

    /**
     * Parse a test URL
     * This method delegates the actual parsing to TestParsingRunner to avoid duplicate code
     *
     * @param url URL of the test page to parse
     * @return List of parsed test questions
     */
    public List<ParsedTestQuestionDto> parse(String url) {
        log.debug("Delegating parsing of URL to TestParsingRunner: {}", url);
        
        try {
            // Process the URL using TestParsingRunner
            boolean success = parsingRunner.processTestUrl(url);
            
            if (!success) {
                throw new ParsingException(
                        ParsingErrorType.PROCESSING_ERROR, // General URL processing error
                        "Failed to process URL: " + url
                );
            }
            
            // Since TestParsingRunner directly saves questions to database,
            // no need to return any questions from here
            return List.of();
        } catch (Exception e) {
            log.error("Error during parsing: {}", e.getMessage(), e);
            throw new ParsingException(
                    ParsingErrorType.PROCESSING_ERROR, // General URL processing error
                    "Error processing URL " + url + ": " + e.getMessage(),
                    e
            );
        }
    }
}