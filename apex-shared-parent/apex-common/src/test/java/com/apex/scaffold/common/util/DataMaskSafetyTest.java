package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.annotation.DataMask;
import com.apex.scaffold.common.annotation.MaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 脱敏安全回归测试。 */
class DataMaskSafetyTest {
    @Test
    void invalidAndShortInputsMustNotLeak() {
        assertThat(DataMaskUtil.maskPhone("secret")).isEqualTo("******");
        assertThat(DataMaskUtil.maskIdCard("abc")).isEqualTo("***");
        assertThat(DataMaskUtil.maskBankCard("12345678")).isEqualTo("********");
        assertThat(DataMaskUtil.maskName("张")).isEqualTo("*");
        assertThat(DataMaskUtil.maskAddress("北京")).isEqualTo("北*");
        assertThat(DataMaskUtil.maskName("   ")).isEmpty();
        assertThat(DataMaskUtil.maskEmail("x@example.com")).isEqualTo("*@example.com");
        assertThat(DataMaskUtil.maskEmail("bad@@secret")).isEqualTo("***********");
        assertThat(DataMaskUtil.maskPhone(null)).isNull();
    }

    @Test
    void unicodeAndJacksonMustUseSameRules() throws Exception {
        assertThat(DataMaskUtil.maskName("𠮷野家")).isEqualTo("𠮷**");
        assertThat(new ObjectMapper().writeValueAsString(new Contact()))
                .contains("138****8000")
                .doesNotContain("13800138000");
    }

    @Test
    void logPhoneMustFailClosedWithoutChangingFingerprint() {
        assertThat(SensitiveDataUtil.maskPhone("abcdefg")).isEqualTo("*******");
        assertThat(SensitiveDataUtil.fingerprint("secret-token")).isEqualTo("sha256:930bbdc51b6a");
    }

    static class Contact {
        @DataMask(type = MaskType.PHONE)
        public String phone = "13800138000";
    }
}
