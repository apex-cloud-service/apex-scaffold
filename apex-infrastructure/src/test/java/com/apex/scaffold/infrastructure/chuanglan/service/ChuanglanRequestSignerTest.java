package com.apex.scaffold.infrastructure.chuanglan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ChuanglanRequestSigner} 协议参数和签名单元测试。
 *
 * <p>工具能力从 API 门面拆出后在其稳定公开边界测试，避免通过反射耦合门面的私有实现。
 *
 * @author Apex Team
 * @since 3.3.0
 */
@DisplayName("创蓝请求签名器")
class ChuanglanRequestSignerTest {

    /** 被测试的无状态协议构建器。 */
    private final ChuanglanRequestSigner signer = new ChuanglanRequestSigner();

    /**
     * 验证模板参数使用合法 JSON 转义，防止验证码内容破坏请求结构。
     */
    @Test
    @DisplayName("模板参数应生成可转义的 JSON 数组")
    void shouldBuildEscapedTemplateParameters() {
        String result = signer.buildTemplateParams("12\"34");

        assertThat(result).isEqualTo("[{\"param1\":\"12\\\"34\"}]");
    }

    /**
     * 验证 nonce 长度、字符范围和连续调用的随机性。
     */
    @Test
    @DisplayName("nonce 应为不同的 32 位小写十六进制字符串")
    void shouldGenerateSecureNonce() {
        String first = signer.nonce();
        String second = signer.nonce();

        assertThat(first).matches("[0-9a-f]{32}");
        assertThat(second).matches("[0-9a-f]{32}").isNotEqualTo(first);
    }

    /**
     * 验证闪验签名在参数插入顺序不同的情况下保持一致。
     */
    @Test
    @DisplayName("闪验签名应先按参数名排序")
    void shouldCanonicalizeFlashParameters() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("token", "token-value");
        first.put("appId", "app-id");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("appId", "app-id");
        second.put("token", "token-value");

        assertThat(signer.signFlash(first, "app-key")).isEqualTo(signer.signFlash(second, "app-key"));
    }
}
