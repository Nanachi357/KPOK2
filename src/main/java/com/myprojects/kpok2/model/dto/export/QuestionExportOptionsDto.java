package com.myprojects.kpok2.model.dto.export;

import com.myprojects.kpok2.model.QuestionType;

import java.util.List;

/** Filter values currently available in the local question database. */
public record QuestionExportOptionsDto(
        List<QuizOption> quizzes,
        List<String> languages,
        List<QuestionType> questionTypes) {

    public QuestionExportOptionsDto {
        quizzes = quizzes == null ? List.of() : List.copyOf(quizzes);
        languages = languages == null ? List.of() : List.copyOf(languages);
        questionTypes = questionTypes == null ? List.of() : List.copyOf(questionTypes);
    }

    public record QuizOption(
            Long quizId,
            String quizUrl,
            List<String> languages,
            long questionCount) {

        public QuizOption {
            languages = languages == null ? List.of() : List.copyOf(languages);
        }

        public String displayName() {
            return quizId + " · " + questionCount + " questions"
                    + (languages.isEmpty()
                    ? ""
                    : " · " + String.join(", ", languages));
        }
    }
}
