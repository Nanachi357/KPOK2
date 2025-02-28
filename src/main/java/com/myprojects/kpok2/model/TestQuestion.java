package com.myprojects.kpok2.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "test_questions")
public class TestQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String normalizedText;

    @ElementCollection
    @CollectionTable(
            name = "question_answers",
            joinColumns = @JoinColumn(name = "question_id")
    )
    @Column(name = "answer")
    private List<String> possibleAnswers;

    private String correctAnswer;
    private String normalizedCorrectAnswer;

    @Column(unique = true)
    private String questionHash;

    private LocalDateTime parsedAt;
    private String sourceUrl;
}