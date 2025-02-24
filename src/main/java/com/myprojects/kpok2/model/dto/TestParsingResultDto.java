package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.service.parser.ParsingStatus;
import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class TestParsingResultDto {
    private String testUrl;
    private ParsingStatus status;
    private List<ParsedTestQuestionDto> questions;
    private String errorMessage;
    private int attemptCount;
}