package com.myprojects.kpok2.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public abstract class TestQuestionMapper {

    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "questionHash", expression = "java(generateHash(dto.getNormalizedText(), dto.getAnswers(), dto.getNormalizedCorrectAnswer()))")
    @Mapping(target = "parsedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "possibleAnswersJson", source = "dto.answers", qualifiedByName = "answersToJson")
    @Mapping(target = "questionText", source = "dto.questionText")
    @Mapping(target = "normalizedText", source = "dto.normalizedText")
    @Mapping(target = "correctAnswer", source = "dto.correctAnswer")
    @Mapping(target = "normalizedCorrectAnswer", source = "dto.normalizedCorrectAnswer")
    public abstract TestQuestion toEntity(ParsedTestQuestionDto dto);

    @Named("answersToJson")
    protected String answersToJson(List<String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert answers to JSON", e);
        }
    }

    protected String generateHash(String normalizedText, List<String> answers, String normalizedCorrectAnswer) {
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