package com.apex.scaffold.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sentinel 资源名称归一化工具单元测试。
 *
 * <p>验证 {@link SentinelResourceUtil} 对健康检查路径的排除、含路径变量 URL
 * 到规则模板名的归一化以及无路径变量路径的透传行为，确保运行时资源名与
 * Sentinel 规则资源名严格对齐。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SentinelResourceUtil 资源名归一化测试")
class SentinelResourceUtilTest {

    /**
     * 测试健康检查相关路径应返回 {@code null}，不受 Sentinel 流控约束。
     */
    @Test
    @DisplayName("健康检查路径应被排除返回null")
    void shouldReturnNullForHealthPaths() {
        assertThat(SentinelResourceUtil.cleanSentinelResource(null)).isNull();
        assertThat(SentinelResourceUtil.cleanSentinelResource("/actuator/health"))
                .isNull();
        assertThat(SentinelResourceUtil.cleanSentinelResource("/actuator/health/liveness"))
                .isNull();
        assertThat(SentinelResourceUtil.cleanSentinelResource("/api/v1/health")).isNull();
        assertThat(SentinelResourceUtil.cleanSentinelResource("/health")).isNull();
    }

    /**
     * 测试含路径变量的运行时 URL 应归一化为规则模板名。
     */
    @Test
    @DisplayName("含路径变量URL应归一化为规则模板名")
    void shouldNormalizePathVariableUrlsToTemplates() {
        assertThat(SentinelResourceUtil.cleanSentinelResource("/api/v1/explore/battles/123/submit"))
                .isEqualTo("/api/v1/explore/battles/{battleId}/submit");
        assertThat(SentinelResourceUtil.cleanSentinelResource("/api/v1/explore/battles/456/skip"))
                .isEqualTo("/api/v1/explore/battles/{battleId}/skip");
        assertThat(SentinelResourceUtil.cleanSentinelResource("/api/v1/user/10086"))
                .isEqualTo("/api/v1/user/{userId}");
        assertThat(SentinelResourceUtil.normalizePathVariables("/api/v1/media/photos/42/vote"))
                .isEqualTo("/api/v1/media/photos/{photoId}/vote");
    }

    /**
     * 测试无路径变量的静态路径应原样透传。
     */
    @Test
    @DisplayName("无路径变量路径应原样返回")
    void shouldReturnRawUrlWhenNoTemplateMatches() {
        assertThat(SentinelResourceUtil.cleanSentinelResource("/api/v1/auth/login"))
                .isEqualTo("/api/v1/auth/login");
        assertThat(SentinelResourceUtil.normalizePathVariables("/api/v1/rankings/rules"))
                .isEqualTo("/api/v1/rankings/rules");
    }

    /**
     * 测试 null 输入在归一化方法中应安全返回 null。
     */
    @Test
    @DisplayName("null输入应安全返回null")
    void shouldReturnNullForNullInput() {
        assertThat(SentinelResourceUtil.normalizePathVariables(null)).isNull();
    }

    /**
     * 测试模板匹配对路径段数量不同与字面值不同的 URL 不应命中。
     */
    @Test
    @DisplayName("路径段数量或字面值不同不应命中模板")
    void shouldNotMatchWhenSegmentsDiffer() {
        // 段数不同：模板 5 段，实际 4 段
        assertThat(SentinelResourceUtil.normalizePathVariables("/api/v1/explore/battles"))
                .isEqualTo("/api/v1/explore/battles");
        // 字面值不同：首段不匹配模板
        assertThat(SentinelResourceUtil.normalizePathVariables("/api/v2/user/1"))
                .isEqualTo("/api/v2/user/1");
        // 空路径变量段不应命中
        assertThat(SentinelResourceUtil.normalizePathVariables("/api/v1/explore/battles//submit"))
                .isEqualTo("/api/v1/explore/battles//submit");
    }
}
