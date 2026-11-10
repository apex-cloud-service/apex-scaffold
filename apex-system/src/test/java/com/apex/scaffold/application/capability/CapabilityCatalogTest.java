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

    @Test
    @DisplayName("开放能力声明 scope，内部治理能力不开放")
    void openScopesAreDeclared() {
        assertThat(CapabilityCatalog.find("file-storage").openScopes())
                .containsExactlyInAnyOrder("open:file:read", "open:file:write");
        assertThat(CapabilityCatalog.find("tenant-governance").openScopes()).isEmpty();
    }

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

    @Test
    @DisplayName("未开放能力不得声明开放 scope")
    void internalCapabilitiesHaveNoOpenScopes() {
        assertThat(CapabilityCatalog.all())
                .filteredOn(capability -> !"active".equals(capability.lifecycle()))
                .allSatisfy(capability -> assertThat(capability.openScopes()).isEmpty());
    }

    @Test
    @DisplayName("未登记能力编码可被识别")
    void detectsUnknownFeatures() {
        assertThat(CapabilityCatalog.unknownFeatures(List.of("workflow", "made-up")))
                .containsExactly("made-up");
        assertThat(CapabilityCatalog.unknownFeatures(List.of())).isEmpty();
        assertThat(CapabilityCatalog.unknownFeatures(null)).isEmpty();
    }

    @Test
    @DisplayName("订阅判定：空 features = 未订阅任何能力，否则精确匹配")
    void subscriptionSemantics() {
        assertThat(CapabilityCatalog.subscribed(Set.of(), "workflow")).isFalse();
        assertThat(CapabilityCatalog.subscribed(null, "workflow")).isFalse();
        assertThat(CapabilityCatalog.subscribed(Set.of("workflow"), "workflow")).isTrue();
        assertThat(CapabilityCatalog.subscribed(Set.of("workflow"), "iot-device"))
                .isFalse();
    }

    @Test
    @DisplayName("PackageScope.allowsCapability 与收紧后的目录订阅语义一致")
    void packageScopeCapability() {
        var full = TenantPackageService.PackageScope.full();
        assertThat(full.allowsCapability("iot-device")).isTrue();

        var scoped = new TenantPackageService.PackageScope(false, Set.of(), Set.of(), Set.of("workflow"));
        assertThat(scoped.allowsCapability("workflow")).isTrue();
        assertThat(scoped.allowsCapability("iot-device")).isFalse();

        var emptyFeatures = new TenantPackageService.PackageScope(false, Set.of(), Set.of(), Set.of());
        assertThat(emptyFeatures.allowsCapability("iot-device")).isFalse();
    }
}
