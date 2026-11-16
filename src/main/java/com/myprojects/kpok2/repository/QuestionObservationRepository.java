package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.QuestionObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionObservationRepository
        extends JpaRepository<QuestionObservation, Long> {

    Optional<QuestionObservation>
            findByRevisionIdAndAnswerKeyIdAndQuizIdAndSourceAttemptIdAndLanguage(
                    Long revisionId,
                    Long answerKeyId,
                    Long quizId,
                    String sourceAttemptId,
                    String language);
}
