package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.annotation.DataMask;
import com.apex.scaffold.common.annotation.MaskType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 验证属性规则互不污染、getter 接线与误标注拒绝输出。 */
class DataMaskJacksonTest {
    @Test
    void supportsGetterCustomNullAndSeparateRules() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var result = mapper.readTree(mapper.writeValueAsString(new Contact()));
        assertThat(result.path("name").asText()).isEqualTo("𠮷**");
        assertThat(result.path("code").asText()).isEqualTo("A***E");
        assertThat(result.path("phone").asText()).isEqualTo("138****8000");
        assertThat(result.path("empty").isNull()).isTrue();
    }

    @Test
    void rejectsNonStringAndInvalidCustomRules() {
        ObjectMapper mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.writeValueAsString(new WrongType())).isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> mapper.writeValueAsString(new WrongRule())).isInstanceOf(JsonMappingException.class);
    }

    static class Contact {
        @DataMask(type = MaskType.CUSTOM, customRule = "1,1")
        public String code = "ABCDE";

        @DataMask(type = MaskType.PHONE)
        public String phone = "13800138000";

        @DataMask(type = MaskType.NAME)
        public String empty;

        @DataMask(type = MaskType.NAME)
        public String getName() {
            return "𠮷野家";
        }
    }

    static class WrongType {
        @DataMask(type = MaskType.PHONE)
        public long phone = 13800138000L;
    }

    static class WrongRule {
        @DataMask(type = MaskType.CUSTOM, customRule = "-1,0")
        public String code = "secret";
    }
}
