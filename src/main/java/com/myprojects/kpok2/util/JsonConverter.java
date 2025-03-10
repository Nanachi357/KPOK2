package com.myprojects.kpok2.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myprojects.kpok2.model.TestQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JsonConverter {
    
    private final ObjectMapper objectMapper;
    
    public List<String> getAnswersFromJson(TestQuestion question) {
        try {
            if (question.getPossibleAnswersJson() == null) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(question.getPossibleAnswersJson(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
} 