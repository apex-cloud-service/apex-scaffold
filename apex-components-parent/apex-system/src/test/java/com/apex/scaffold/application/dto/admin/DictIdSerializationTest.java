package com.apex.scaffold.application.dto.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DictIdSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesLargeDictionaryIdsWithoutLosingPrecision() throws Exception {
        long id = 9_007_199_254_740_993L;
        for (Object dto : new Object[] {
            DictTypeDTO.builder().id(id).build(), DictDataDTO.builder().id(id).build()
        }) {
            var jsonId = mapper.readTree(mapper.writeValueAsBytes(dto)).path("id");
            assertThat(jsonId.isTextual()).isTrue();
            assertThat(jsonId.textValue()).isEqualTo("9007199254740993");
        }
    }
}
