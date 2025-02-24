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
    @Mapping(target = "questionHash", source = "questionText", qualifiedByName = "generateHash")
    @Mapping(target = "parsedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "possibleAnswers", source = "answers")
    TestQuestion toEntity(ParsedTestQuestionDto dto, String sourceUrl);

    @Named("generateHash")
    default String generateHash(String questionText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(questionText.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
}