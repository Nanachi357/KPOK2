package com.myprojects.kpok2.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Utility class for TestQuestionMapper with helper methods
 */
@Component
public class TestQuestionMapperUtils {
    
    private static ObjectMapper objectMapper;
    
    public TestQuestionMapperUtils(ObjectMapper objectMapper) {
        TestQuestionMapperUtils.objectMapper = objectMapper;
    }
    
    /**
     * Converts list of answers to JSON
     */
    public static String answersToJson(List<String> answers) {
        if (answers == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert answers to JSON", e);
        }
    }

    /**
     * Generates hash based on question content
     */
    public static String generateHash(String normalizedText, List<String> answers, String normalizedCorrectAnswer) {
        if (normalizedText == null || answers == null || normalizedCorrectAnswer == null) {
            throw new IllegalArgumentException("Cannot generate hash from null values");
        }
        
        List<String> sortedAnswers = new ArrayList<>(answers);
        Collections.sort(sortedAnswers);
        
        String combinedData = normalizedText + "|" + 
                            String.join("|", sortedAnswers) + "|" + 
                            normalizedCorrectAnswer;
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(combinedData.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
} 