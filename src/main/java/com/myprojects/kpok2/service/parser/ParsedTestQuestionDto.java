package com.myprojects.kpok2.service.parser;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class ParsedTestQuestionDto {
    private String questionText;
    private List<String> answers;
    private String correctAnswer;
}