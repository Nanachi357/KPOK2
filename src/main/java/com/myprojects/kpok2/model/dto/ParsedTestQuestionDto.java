package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.QuestionType;
import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class ParsedTestQuestionDto {
    private String questionText;
    private String normalizedText;
    private String contextText;
    private String normalizedContext;
    private String contextHtml;
    private String questionHtml;
    private List<String> answers;
    private List<String> answerHtml;
    private List<ParsedQuestionAssetDto> assets;
    private String canonicalContentFingerprint;
    private String revisionContentFingerprint;
    private List<String> legacyAnswers;
    private List<String> normalizedAnswers;
    private List<String> correctAnswers;
    private List<Integer> correctAnswerIndices;
    private List<String> normalizedCorrectAnswers;
    private String correctAnswer;
    private String normalizedCorrectAnswer;
    private String legacyNormalizedText;
    private String legacyNormalizedCorrectAnswer;
    private int normalizationVersion;
    private QuestionType questionType;
    private Long sourceQuizId;
    private String sourceUrl;
    private String sourceLanguage;
    private String sourceAttemptId;
}
