package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.QuestionOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface QuestionOriginRepository extends JpaRepository<QuestionOrigin, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO question_origins
                (question_id, quiz_id, quiz_url, language, first_seen_at, last_seen_at)
            VALUES
                (:questionId, :quizId, :quizUrl, :language, :observedAt, :observedAt)
            ON CONFLICT (question_id, quiz_id, language)
            DO UPDATE SET
                last_seen_at = EXCLUDED.last_seen_at,
                quiz_url = CASE
                    WHEN question_origins.quiz_url IS NULL
                         OR BTRIM(question_origins.quiz_url) = ''
                    THEN EXCLUDED.quiz_url
                    ELSE question_origins.quiz_url
                END
            """, nativeQuery = true)
    int upsertOrigin(
            @Param("questionId") Long questionId,
            @Param("quizId") Long quizId,
            @Param("quizUrl") String quizUrl,
            @Param("language") String language,
            @Param("observedAt") LocalDateTime observedAt);

    @Modifying
    @Query(value = """
            INSERT INTO question_origins
                (question_id, quiz_id, quiz_url, language, first_seen_at, last_seen_at)
            SELECT
                q.id,
                q.source_quiz_id,
                COALESCE(q.source_url, ''),
                COALESCE(NULLIF(LOWER(BTRIM(q.source_language)), ''), 'und'),
                COALESCE(q.parsed_at, CURRENT_TIMESTAMP),
                COALESCE(q.parsed_at, CURRENT_TIMESTAMP)
            FROM test_questions q
            WHERE q.source_quiz_id IS NOT NULL
            ON CONFLICT (question_id, quiz_id, language) DO NOTHING
            """, nativeQuery = true)
    int backfillLegacyOrigins();
}
