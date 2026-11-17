package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.QuestionType;

import java.util.List;

/** Display/export representation of one revision paired with one answer key. */
public record QuestionRecordDto(
        Long questionId,
        Long revisionId,
        Long answerKeyId,
        String contextText,
        String questionText,
        List<String> answers,
        List<String> correctAnswers,
        QuestionType questionType,
        boolean answerKeyConflict,
        String contextHtml,
        String questionHtml,
        List<String> answerHtml,
        List<QuestionAssetRecordDto> assets) {

    public QuestionRecordDto {
        answers = answers == null ? List.of() : List.copyOf(answers);
        correctAnswers = correctAnswers == null ? List.of() : List.copyOf(correctAnswers);
        answerHtml = answerHtml == null ? List.of() : List.copyOf(answerHtml);
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public QuestionRecordDto(
            Long questionId,
            Long revisionId,
            Long answerKeyId,
            String contextText,
            String questionText,
            List<String> answers,
            List<String> correctAnswers,
            QuestionType questionType,
            boolean answerKeyConflict) {
        this(
                questionId,
                revisionId,
                answerKeyId,
                contextText,
                questionText,
                answers,
                correctAnswers,
                questionType,
                answerKeyConflict,
                null,
                null,
                List.of(),
                List.of());
    }

    public String correctAnswerText() {
        return String.join(" | ", correctAnswers);
    }
}
