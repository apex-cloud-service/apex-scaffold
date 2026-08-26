package com.apex.scaffold.infrastructure.chuanglan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * ChuanglanPropertiesTest 单元测试。
 *
 * <p>覆盖核心正常流程、参数边界与失败分支，防止后续修改破坏既有行为。</p>
 *
 * @author Apex Team
 */
class ChuanglanPropertiesTest {

    /**
     * 执行 {@code explicitPlatformMustHaveItsOwnCredentials} 操作。
     */
    @Test
    void explicitPlatformMustHaveItsOwnCredentials() {
        ChuanglanProperties.Flash flash = new ChuanglanProperties.Flash();
        assertThatThrownBy(() -> flash.getPlatformConfig("ios"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ios");
    }

    /**
     * 执行 {@code unknownPlatformIsRejected} 操作。
     */
    @Test
    void unknownPlatformIsRejected() {
        ChuanglanProperties.Flash flash = new ChuanglanProperties.Flash();
        assertThatThrownBy(() -> flash.getPlatformConfig("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 执行 {@code legacyCredentialsRemainAvailableOnlyForDefaultPlatform} 操作。
     */
    @Test
    @SuppressWarnings("deprecation")
    void legacyCredentialsRemainAvailableOnlyForDefaultPlatform() {
        ChuanglanProperties.Flash flash = new ChuanglanProperties.Flash();
        flash.setAppId("legacy-id");
        flash.setAppKey("legacy-key");

        assertThat(flash.getPlatformConfig(null).getAppId()).isEqualTo("legacy-id");
    }
}
