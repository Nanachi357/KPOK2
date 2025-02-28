package com.myprojects.kpok2.model.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class ParsedTestQuestionDto {
    private String questionText;
    private String normalizedText;
    private List<String> answers;
    private String correctAnswer;
    private String normalizedCorrectAnswer;
}