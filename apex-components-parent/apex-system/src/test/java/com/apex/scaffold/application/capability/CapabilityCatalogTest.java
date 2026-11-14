package com.apex.scaffold.application.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.service.TenantPackageService;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 能力目录与订阅判定测试（方案 M4 / 批次 D4）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("能力目录与订阅判定测试")
class CapabilityCatalogTest {

    /**
     * 验证目录规模、编码唯一性与七个业务能力中心的覆盖关系。
     */
    @Test
    @DisplayName("目录登记完整：能力编码唯一，七大中心齐备")
    void catalogIsComplete() {
        var all = CapabilityCatalog.all();
        assertThat(all).hasSize(8);
        assertThat(all).extracting(CapabilityCatalog.Capability::code).doesNotHaveDuplicates();
        assertThat(all)
                .extracting(CapabilityCatalog.Capability::center)
                .contains("认证授权中心", "用户组织中心", "工作流中心", "文件中心", "消息中心", "租户治理中心", "集成中心");
    }

    /**
     * 验证八项能力均已投入运行，特别锁定集成中心两项能力的生命周期。
     */
    @Test
    @DisplayName("全部能力均已投入运行，包括集成中心两项能力")
    void allCapabilitiesAreActive() {
        assertThat(CapabilityCatalog.all()).allSatisfy(capability -> assertThat(capability.lifecycle())
                .as("能力 %s 的生命周期", capability.code())
                .isEqualTo("active"));
        assertThat(CapabilityCatalog.find("integration-connector").lifecycle())
                .as("系统集成连接器已投入运行")
                .isEqualTo("active");
        assertThat(CapabilityCatalog.find("iot-device").lifecycle())
                .as("物联网设备接入已投入运行")
                .isEqualTo("active");
    }

    /**
     * 验证开放能力声明最小必要 scope，内部治理能力不对外暴露 scope。
     */
    @Test
    @DisplayName("开放能力声明 scope，内部治理能力不开放")
    void openScopesAreDeclared() {
        assertThat(CapabilityCatalog.find("file-storage").openScopes())
                .containsExactlyInAnyOrder("open:file:read", "open:file:write");
        assertThat(CapabilityCatalog.find("tenant-governance").openScopes()).isEmpty();
    }

    /**
     * 验证所有能力均具备负责人、语义化版本、消费方、SLO 与合法生命周期。
     */
    @Test
    @DisplayName("每项能力都具备完整治理元数据")
    void governanceMetadataIsComplete() {
        var capabilities = CapabilityCatalog.all();
        assertThat(capabilities).allSatisfy(capability -> {
            assertThat(capability.owner()).isNotBlank();
            assertThat(capability.version()).matches(Pattern.compile("\\d+\\.\\d+\\.\\d+"));
            assertThat(capability.consumers()).isNotEmpty();
            assertThat(capability.slo()).isNotBlank();
            assertThat(capability.lifecycle()).isIn("planned", "active", "deprecated", "retired");
        });
    }

    /**
     * 验证内部与尚未开放 scope 的能力不会错误地声明开放授权范围。
     */
    @Test
    @DisplayName("内部治理能力不开放，对外能力声明对应 scope")
    void internalCapabilitiesHaveNoOpenScopes() {
        assertThat(CapabilityCatalog.find("tenant-governance").openScopes())
                .as("租户治理属于平台内部治理能力")
                .isEmpty();
        assertThat(CapabilityCatalog.find("integration-connector").openScopes())
                .as("集成连接器当前未开放第三方 scope")
                .isEmpty();
        assertThat(CapabilityCatalog.find("iot-device").openScopes())
                .as("物联网设备接入当前未开放第三方 scope")
                .isEmpty();
        assertThat(CapabilityCatalog.find("identity-auth").openScopes()).contains("open:user:read");
        assertThat(CapabilityCatalog.find("user-org").openScopes()).contains("open:user:read");
        assertThat(CapabilityCatalog.find("workflow").openScopes())
                .containsExactlyInAnyOrder("open:workflow:read", "open:workflow:write");
        assertThat(CapabilityCatalog.find("file-storage").openScopes())
                .containsExactlyInAnyOrder("open:file:read", "open:file:write");
        assertThat(CapabilityCatalog.find("office-message").openScopes()).contains("open:message:send");
    }

    /**
     * 验证未知能力编码可被识别，空集合和 {@code null} 均不产生误报。
     */
    @Test
    @DisplayName("未登记能力编码可被识别")
    void detectsUnknownFeatures() {
        assertThat(CapabilityCatalog.unknownFeatures(List.of("workflow", "made-up")))
                .containsExactly("made-up");
        assertThat(CapabilityCatalog.unknownFeatures(List.of())).isEmpty();
        assertThat(CapabilityCatalog.unknownFeatures(null)).isEmpty();
    }

    /**
     * 验证能力订阅使用精确匹配，并明确空集合表示未订阅任何能力。
     */
    @Test
    @DisplayName("订阅判定：空 features = 未订阅任何能力，否则精确匹配")
    void subscriptionSemantics() {
        assertThat(CapabilityCatalog.subscribed(Set.of(), "workflow")).isFalse();
        assertThat(CapabilityCatalog.subscribed(null, "workflow")).isFalse();
        assertThat(CapabilityCatalog.subscribed(Set.of("workflow"), "workflow")).isTrue();
        assertThat(CapabilityCatalog.subscribed(Set.of("workflow"), "iot-device"))
                .isFalse();
    }

    /**
     * 验证套餐全量范围与受限范围的能力判定口径保持一致。
     */
    @Test
    @DisplayName("PackageScope.allowsCapability 与收紧后的目录订阅语义一致")
    void packageScopeCapability() {
        var full = TenantPackageService.PackageScope.full();
        assertThat(full.allowsCapability("iot-device")).isTrue();

        var scoped = new TenantPackageService.PackageScope(false, false, Set.of(), Set.of(), Set.of("workflow"));
        assertThat(scoped.allowsCapability("workflow")).isTrue();
        assertThat(scoped.allowsCapability("iot-device")).isFalse();

        var emptyFeatures = new TenantPackageService.PackageScope(false, false, Set.of(), Set.of(), Set.of());
        assertThat(emptyFeatures.allowsCapability("iot-device")).isFalse();
    }
}
