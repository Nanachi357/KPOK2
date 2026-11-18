package com.myprojects.kpok2.model.dto;

import com.myprojects.kpok2.model.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

/** Complete read-only library view of one canonical question. */
public record QuestionLibraryDetailDto(
        Long questionId,
        String questionHash,
        String contextText,
        String questionText,
        QuestionType questionType,
        List<Origin> origins,
        List<Revision> revisions) {

    public QuestionLibraryDetailDto {
        origins = origins == null ? List.of() : List.copyOf(origins);
        revisions = revisions == null ? List.of() : List.copyOf(revisions);
    }

    public record Origin(
            Long quizId,
            String quizUrl,
            String language,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt) {
    }

    public record Revision(
            Long revisionId,
            String revisionHash,
            String contextText,
            String questionText,
            List<String> answers,
            QuestionType questionType,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            boolean answerKeyConflict,
            List<AnswerKey> answerKeys) {

        public Revision {
            answers = answers == null ? List.of() : List.copyOf(answers);
            answerKeys = answerKeys == null ? List.of() : List.copyOf(answerKeys);
        }
    }

    public record AnswerKey(
            Long answerKeyId,
            String keyHash,
            List<String> correctAnswers,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt) {

        public AnswerKey {
            correctAnswers = correctAnswers == null
                    ? List.of()
                    : List.copyOf(correctAnswers);
        }
    }
}
