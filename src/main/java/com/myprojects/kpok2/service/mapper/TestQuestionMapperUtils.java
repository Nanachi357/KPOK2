package com.myprojects.kpok2.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.QuestionType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Utility class for TestQuestionMapper with helper methods
 */
public class TestQuestionMapperUtils {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestQuestionMapperUtils() {
    }
    
    /**
     * Converts list of answers to JSON
     */
    public static String answersToJson(List<String> answers) {
        if (answers == null) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert answers to JSON", e);
        }
    }

    /**
     * Generates hash based on question content
     */
    public static String generateHash(String normalizedText, List<String> answers, String normalizedCorrectAnswer) {
        return generateLegacyHash(normalizedText, answers, normalizedCorrectAnswer);
    }

    /**
     * Reproduces the original hash format so v2 parsing can recognize rows
     * that were stored before Unicode-aware normalization was introduced.
     */
    public static String generateLegacyHash(
            String normalizedText, List<String> answers, String normalizedCorrectAnswer) {
        if (normalizedText == null || answers == null || normalizedCorrectAnswer == null) {
            throw new IllegalArgumentException("Cannot generate hash from null values");
        }
        
        // Clean prefix patterns (like "a.", "b.") from answers before sorting
        List<String> cleanedAnswers = new ArrayList<>();
        for (String answer : answers) {
            // Remove prefixes like "a.", "b.", etc. from the beginning of answers
            String cleaned = answer.replaceAll("^[a-z]\\.", "").trim();
            cleanedAnswers.add(cleaned);
        }
        
        // Sort cleaned answers to ensure consistent ordering
        Collections.sort(cleanedAnswers);
        
        String combinedData = normalizedText + "|" + 
                            String.join("|", cleanedAnswers) + "|" + 
                            normalizedCorrectAnswer;

        return md5(combinedData, false);
    }

    /**
     * Generates a deterministic v2 hash from normalized values only.
     */
    public static String generateHashV2(
            String normalizedText,
            List<String> normalizedAnswers,
            List<String> normalizedCorrectAnswers) {
        if (normalizedText == null || normalizedAnswers == null || normalizedCorrectAnswers == null) {
            throw new IllegalArgumentException("Cannot generate hash from null values");
        }

        List<String> sortedAnswers = new ArrayList<>(normalizedAnswers);
        Collections.sort(sortedAnswers);
        List<String> sortedCorrectAnswers = new ArrayList<>(normalizedCorrectAnswers);
        Collections.sort(sortedCorrectAnswers);

        String combinedData = normalizedText + "|"
                + String.join("|", sortedAnswers) + "|"
                + String.join("|", sortedCorrectAnswers);

        return md5(combinedData, true);
    }

    /**
     * Generates a context-aware hash. Questions without a context retain the
     * v2 hash so upgrading does not duplicate ordinary quiz questions.
     */
    public static String generateHashV3(
            String normalizedContext,
            String normalizedText,
            List<String> normalizedAnswers,
            List<String> normalizedCorrectAnswers) {
        if (normalizedContext == null || normalizedContext.isBlank()) {
            return generateHashV2(normalizedText, normalizedAnswers, normalizedCorrectAnswers);
        }

        if (normalizedText == null || normalizedAnswers == null
                || normalizedCorrectAnswers == null) {
            throw new IllegalArgumentException("Cannot generate hash from null values");
        }

        List<String> sortedAnswers = new ArrayList<>(normalizedAnswers);
        Collections.sort(sortedAnswers);
        List<String> sortedCorrectAnswers = new ArrayList<>(normalizedCorrectAnswers);
        Collections.sort(sortedCorrectAnswers);

        String combinedData = normalizedContext + "|" + normalizedText + "|"
                + String.join("|", sortedAnswers) + "|"
                + String.join("|", sortedCorrectAnswers);
        return md5(combinedData, true);
    }

    /** Stable identity of the question wording, independent of options and keys. */
    public static String generateCanonicalHash(
            String normalizedContext, String normalizedText) {
        if (normalizedText == null) {
            throw new IllegalArgumentException("Cannot generate canonical hash from null text");
        }
        String context = normalizedContext == null ? "" : normalizedContext;
        return md5(context + "|" + normalizedText, true);
    }

    /** Identity of an option-set revision, independent of display order. */
    public static String generateRevisionHash(
            String canonicalHash,
            List<String> normalizedAnswers,
            QuestionType questionType) {
        if (canonicalHash == null || normalizedAnswers == null || questionType == null) {
            throw new IllegalArgumentException("Cannot generate revision hash from null values");
        }
        List<String> sortedAnswers = new ArrayList<>(normalizedAnswers);
        Collections.sort(sortedAnswers);
        return md5(canonicalHash + "|" + questionType + "|"
                + String.join("|", sortedAnswers), true);
    }

    /** Identity of a correct-answer set, independent of display order. */
    public static String generateAnswerKeyHash(List<String> normalizedCorrectAnswers) {
        if (normalizedCorrectAnswers == null) {
            throw new IllegalArgumentException("Cannot generate answer-key hash from null values");
        }
        List<String> sortedAnswers = new ArrayList<>(normalizedCorrectAnswers);
        Collections.sort(sortedAnswers);
        return md5(String.join("|", sortedAnswers), true);
    }

    private static String md5(String value, boolean forceUtf8) {
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(forceUtf8
                    ? value.getBytes(StandardCharsets.UTF_8)
                    : value.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
}
