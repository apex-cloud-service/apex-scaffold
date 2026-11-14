package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 敏感数据脱敏工具单元测试。
 *
 * @author Apex Team
 * @since 3.2.0
 */
class SensitiveDataUtilTest {

    /**
     * 验证手机号只保留必要定位信息。
     *
     */
    @Test
    @DisplayName("手机号应按前三后四规则脱敏")
    void masksPhone() {
        assertThat(SensitiveDataUtil.maskPhone("13800138000")).isEqualTo("138****8000");
        assertThat(SensitiveDataUtil.maskPhone(null)).isEqualTo("***");
    }

    /**
     * 验证指纹稳定且不包含原始值。
     *
     */
    @Test
    @DisplayName("敏感标识应生成稳定不可逆指纹")
    void fingerprintsIdentifier() {
        String fingerprint = SensitiveDataUtil.fingerprint("secret-token");

        assertThat(fingerprint).isEqualTo(SensitiveDataUtil.fingerprint("secret-token"));
        assertThat(fingerprint).startsWith("sha256:").doesNotContain("secret-token");
    }
}
