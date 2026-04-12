package com.pawever.backend.global.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class IntegerListJsonConverter implements AttributeConverter<List<Integer>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Integer>> INTEGER_LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("정수 목록을 JSON으로 변환하지 못했습니다.", e);
        }
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return new ArrayList<>(OBJECT_MAPPER.readValue(dbData, INTEGER_LIST_TYPE));
        } catch (Exception e) {
            throw new IllegalArgumentException("정수 목록 JSON을 읽지 못했습니다.", e);
        }
    }
}
