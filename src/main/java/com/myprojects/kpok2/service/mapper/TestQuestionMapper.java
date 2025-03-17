package com.myprojects.kpok2.service.mapper;

import com.myprojects.kpok2.model.TestQuestion;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;

/**
 * MapStruct mapper for TestQuestion entities
 * Uses declarative approach with annotations
 */
@Mapper(
    componentModel = "spring", 
    imports = {LocalDateTime.class},
    uses = {TestQuestionMapperUtils.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TestQuestionMapper {

    /**
     * Converts DTO to entity using MapStruct
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "questionHash", expression = "java(TestQuestionMapperUtils.generateHash(dto.getNormalizedText(), dto.getAnswers(), dto.getNormalizedCorrectAnswer()))")
    @Mapping(target = "parsedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "possibleAnswersJson", expression = "java(TestQuestionMapperUtils.answersToJson(dto.getAnswers()))")
    @Mapping(target = "questionText", source = "dto.questionText")
    @Mapping(target = "normalizedText", source = "dto.normalizedText")
    @Mapping(target = "correctAnswer", source = "dto.correctAnswer")
    @Mapping(target = "normalizedCorrectAnswer", source = "dto.normalizedCorrectAnswer")
    TestQuestion toEntity(ParsedTestQuestionDto dto);
}