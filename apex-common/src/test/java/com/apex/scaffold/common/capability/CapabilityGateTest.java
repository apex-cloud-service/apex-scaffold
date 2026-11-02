package com.apex.scaffold.common.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CapabilityGate} 判定矩阵单测（D4 共用口径）.
 *
 * <p>覆盖判定矩阵的六类输入：null 输入 fail-closed、不限量租户、空特性集合
 * （沿用 {@code TenantPackageService.allowsCapability} 的全量订阅语义）、
 * 精确包含、特性缺失与空白能力编码。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("CapabilityGate 能力订阅判定矩阵测试")
class CapabilityGateTest {

    @Test
    @DisplayName("null 特性集合按未订阅处理（fail-closed）")
    void shouldRejectNullFeatures() {
        assertThat(CapabilityGate.subscribed(null, "file-storage")).isFalse();
    }

    @Test
    @DisplayName("null 或空白能力编码按未订阅处理")
    void shouldRejectNullOrBlankCapability() {
        assertThat(CapabilityGate.subscribed(TenantFeatures.of(Set.of("file-storage")), null))
                .isFalse();
        assertThat(CapabilityGate.subscribed(TenantFeatures.of(Set.of("file-storage")), "  "))
                .isFalse();
    }

    @Test
    @DisplayName("不限量租户订阅任意能力，包括未登记编码")
    void shouldAllowUnboundedTenantForAnyCapability() {
        assertThat(CapabilityGate.subscribed(TenantFeatures.unlimited(), "file-storage"))
                .isTrue();
        assertThat(CapabilityGate.subscribed(TenantFeatures.unlimited(), "any-capability"))
                .isTrue();
    }

    @Test
    @DisplayName("空特性集合沿用既有全量订阅口径")
    void shouldTreatEmptyFeaturesAsFullSubscription() {
        assertThat(CapabilityGate.subscribed(TenantFeatures.of(Set.of()), "file-storage"))
                .isTrue();
        // null 特性集合由 TenantFeatures 兼容构造收敛为空集合，语义一致
        assertThat(CapabilityGate.subscribed(new TenantFeatures(false, null), "file-storage"))
                .isTrue();
    }

    @Test
    @DisplayName("特性集合精确包含判定：命中放行、缺失拒绝")
    void shouldMatchByExactContainment() {
        TenantFeatures features = TenantFeatures.of(Set.of("file-storage", "workflow"));
        assertThat(CapabilityGate.subscribed(features, "file-storage")).isTrue();
        assertThat(CapabilityGate.subscribed(features, "workflow")).isTrue();
        assertThat(CapabilityGate.subscribed(features, "user-org")).isFalse();
        assertThat(CapabilityGate.subscribed(features, "office-message")).isFalse();
    }

    @Test
    @DisplayName("大小写敏感：编码不一致视为未订阅")
    void shouldBeCaseSensitive() {
        TenantFeatures features = TenantFeatures.of(Set.of("file-storage"));
        assertThat(CapabilityGate.subscribed(features, "File-Storage")).isFalse();
    }
}
