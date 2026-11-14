package com.apex.scaffold.application.openplatform;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.openplatform.OpenPlatformScopeRegistry.ScopeDefinition;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OpenPlatformScopeRegistry} 单元测试（D2）.
 *
 * <p>覆盖内置目录与 {@code docs/03-api/open-platform-integration.md} scope 契约的对齐、
 * 配置驱动扩展与同名覆盖、空配置键容错以及 {@code invalidScopes} 越权校验。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("OpenPlatformScopeRegistry 开放平台 scope 注册表测试")
class OpenPlatformScopeRegistryTest {

    @Test
    @DisplayName("内置目录应覆盖文档契约的 6 个 scope 并携带上游服务/权限/能力映射")
    void builtinScopesShouldAlignWithContract() {
        OpenPlatformScopeRegistry registry = new OpenPlatformScopeRegistry();

        List<ScopeDefinition> definitions = registry.list();
        assertThat(definitions).hasSize(6);
        assertThat(definitions)
                .extracting(ScopeDefinition::getScope)
                .containsExactly(
                        "open:file:read",
                        "open:file:write",
                        "open:workflow:read",
                        "open:workflow:write",
                        "open:user:read",
                        "open:message:send");

        ScopeDefinition fileRead = registry.find("open:file:read");
        assertThat(fileRead).isNotNull();
        assertThat(fileRead.getUpstreamService()).isEqualTo("apex-file");
        assertThat(fileRead.getPermission()).isEqualTo("file:read");
        assertThat(fileRead.getCapability()).isEqualTo("file-storage");

        ScopeDefinition messageSend = registry.find("open:message:send");
        assertThat(messageSend.getUpstreamService()).isEqualTo("apex-office");
        assertThat(messageSend.getPermission()).isEqualTo("office:message:send");
        assertThat(messageSend.getCapability()).isEqualTo("office-message");
    }

    @Test
    @DisplayName("find/isValid 对未登记与空白 scope 应返回 null/false")
    void unknownOrBlankScopeShouldBeInvalid() {
        OpenPlatformScopeRegistry registry = new OpenPlatformScopeRegistry();

        assertThat(registry.find("open:file:delete")).isNull();
        assertThat(registry.find("")).isNull();
        assertThat(registry.find(null)).isNull();
        assertThat(registry.isValid("open:user:read")).isTrue();
        assertThat(registry.isValid("admin:user:read")).isFalse();
        assertThat(registry.isValid(" ")).isFalse();
    }

    @Test
    @DisplayName("配置驱动扩展应新增未内置 scope；同名配置应覆盖内置定义")
    void configurationShouldExtendAndOverrideBuiltin() {
        OpenPlatformScopeRegistry registry = new OpenPlatformScopeRegistry();
        registry.setScopes(List.of(
                new ScopeDefinition(
                        "open:iot:telemetry", "设备遥测", "apex-example", "iot:telemetry", "iot", "配置扩展的新 scope"),
                new ScopeDefinition(
                        "open:file:read", "文件读取（合作方定制）", "apex-file", "file:read", "file-storage", "同名覆盖")));

        List<ScopeDefinition> definitions = registry.list();
        assertThat(definitions).hasSize(7);

        ScopeDefinition iot = registry.find("open:iot:telemetry");
        assertThat(iot).isNotNull();
        assertThat(iot.getCapability()).isEqualTo("iot");

        ScopeDefinition overridden = registry.find("open:file:read");
        assertThat(overridden).isNotNull();
        assertThat(overridden.getName()).isEqualTo("文件读取（合作方定制）");
        assertThat(overridden.getUpstreamService()).isEqualTo("apex-file");
    }

    @Test
    @DisplayName("未声明 scope 键的配置项应被忽略，不破坏目录")
    void blankScopeKeyShouldBeIgnored() {
        OpenPlatformScopeRegistry registry = new OpenPlatformScopeRegistry();
        registry.setScopes(List.of(new ScopeDefinition(null, "缺失键", "apex-x", null, null, null)));

        assertThat(registry.list()).hasSize(6);
    }

    @Test
    @DisplayName("invalidScopes 应只返回未登记部分，空/null 输入返回空清单")
    void invalidScopesShouldReportOnlyUnknownEntries() {
        OpenPlatformScopeRegistry registry = new OpenPlatformScopeRegistry();

        assertThat(registry.invalidScopes(null)).isEmpty();
        assertThat(registry.invalidScopes(Set.of())).isEmpty();
        assertThat(registry.invalidScopes(Set.of("open:file:read", "open:file:write")))
                .isEmpty();
        assertThat(registry.invalidScopes(Set.of("open:file:read", "tenant:read", "open:nope")))
                .containsExactlyInAnyOrder("tenant:read", "open:nope");
    }
}
