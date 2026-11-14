package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.*;

import com.apex.scaffold.common.annotation.MaskType;
import com.apex.scaffold.common.masking.JsonFieldMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JSON 字段批量脱敏契约测试。 */
class JsonFieldMaskerTest {
    @Test
    void recursivelyMasksWithoutMutatingInput() throws Exception {
        var input = new ObjectMapper()
                .readTree("{\"phone\":13800138000,\"rows\":[{\"phone\":\"13800138000\",\"other\":1}],\"null\":null}");
        var output = JsonFieldMasker.mask(input, Map.of("phone", new JsonFieldMasker.Rule(MaskType.PHONE, "")));
        assertThat(output.get("phone").asText()).isEqualTo("***");
        assertThat(output.at("/rows/0/phone").asText()).isEqualTo("138****8000");
        assertThat(input.at("/rows/0/phone").asText()).isEqualTo("13800138000");
        assertThat(output.at("/rows/0/other").asInt()).isEqualTo(1);
    }

    @Test
    void validatesCustomRulesAndProtectsShortUnicode() {
        assertThat(DataMaskUtil.mask("𠮷野家東京", MaskType.CUSTOM, "1,1")).isEqualTo("𠮷***京");
        assertThat(DataMaskUtil.maskRange("ab", Integer.MAX_VALUE, 1)).isEqualTo("**");
        assertThatThrownBy(() -> new JsonFieldMasker.Rule(MaskType.CUSTOM, "-1,2"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
