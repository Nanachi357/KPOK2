package com.myprojects.kpok2.service.mapper;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public interface TestQuestionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "questionHash", source = "dto.normalizedText", qualifiedByName = "generateHash")
    @Mapping(target = "parsedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "possibleAnswers", source = "dto.answers")
    @Mapping(target = "questionText", source = "dto.questionText")
    @Mapping(target = "normalizedText", source = "dto.normalizedText")
    @Mapping(target = "correctAnswer", source = "dto.correctAnswer")
    @Mapping(target = "normalizedCorrectAnswer", source = "dto.normalizedCorrectAnswer")
    @Mapping(target = "sourceUrl", source = "sourceUrl")
    TestQuestion toEntity(ParsedTestQuestionDto dto, String sourceUrl);

    @Named("generateHash")
    default String generateHash(String normalizedText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(normalizedText.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
}