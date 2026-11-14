package com.apex.scaffold.application.openplatform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开放平台 scope 注册表（D2，system 侧单一数据源）。
 *
 * <p>内置目录与 {@code docs/03-api/open-platform-integration.md} 的 scope 契约对齐
 * （{@code open:<domain>:<action>}，scope → 上游服务/下游权限码/订阅能力映射）；
 * 可经 {@code apex.open-platform.scopes} 配置扩展或同名覆盖（如预登记尚未内置的能力 scope）。
 * 开放应用注册校验、管理端 scope 清单查询与网关路由断言共用本目录。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "apex.open-platform")
public class OpenPlatformScopeRegistry {

    /** 配置驱动的 scope 扩展/覆盖清单（按 scope 键与内置目录合并，setter 供配置绑定） */
    @Setter
    private List<ScopeDefinition> scopes = new ArrayList<>();

    /** 配置项绑定用可变载体。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScopeDefinition {

        /** scope 标识（{@code open:<domain>:<action>}） */
        private String scope;

        /** 展示名称 */
        private String name;

        /** 上游服务（Maven 模块/归属组件） */
        private String upstreamService;

        /** scope 对应的下游权限码 */
        private String permission;

        /** 网关能力门禁使用的租户订阅能力编码 */
        private String capability;

        /** 用途说明 */
        private String description;
    }

    /**
     * 合并后的有效 scope 目录（内置目录 + 配置扩展，配置同名覆盖）.
     *
     * @return 不可变清单，插入顺序稳定
     */
    public List<ScopeDefinition> list() {
        Map<String, ScopeDefinition> merged = new LinkedHashMap<>();
        for (ScopeDefinition definition : builtinScopes()) {
            merged.put(definition.getScope(), definition);
        }
        for (ScopeDefinition definition : scopes) {
            if (definition.getScope() != null && !definition.getScope().isBlank()) {
                merged.put(definition.getScope(), definition);
            } else {
                log.warn("忽略未声明 scope 键的开放平台配置项: {}", definition);
            }
        }
        return List.copyOf(merged.values());
    }

    /**
     * 按 scope 查询定义.
     *
     * @param scope scope 标识
     * @return 定义；未登记返回 {@code null}
     */
    public ScopeDefinition find(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        return list().stream()
                .filter(definition -> scope.equals(definition.getScope()))
                .findFirst()
                .orElse(null);
    }

    /**
     * scope 是否在目录内.
     *
     * @param scope scope 标识
     * @return 已登记返回 {@code true}
     */
    public boolean isValid(String scope) {
        return find(scope) != null;
    }

    /**
     * 校验申请 scope 集合，返回其中未登记的部分.
     *
     * @param candidates 申请 scope 集合（可空）
     * @return 非法 scope 列表（空表示全部合法）
     */
    public List<String> invalidScopes(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream().filter(scope -> !isValid(scope)).toList();
    }

    private static List<ScopeDefinition> builtinScopes() {
        List<ScopeDefinition> definitions = new ArrayList<>();
        definitions.add(new ScopeDefinition(
                "open:file:read", "文件读取", "apex-file", "file:read", "file-storage", "读取文件与对象存储元数据"));
        definitions.add(
                new ScopeDefinition("open:file:write", "文件写入", "apex-file", "file:write", "file-storage", "上传与写入文件"));
        definitions.add(new ScopeDefinition(
                "open:workflow:read", "工作流读取", "apex-workflow", "workflow:read", "workflow", "查询流程定义、实例与任务"));
        definitions.add(new ScopeDefinition(
                "open:workflow:write", "工作流发起", "apex-workflow", "workflow:write", "workflow", "发起与推进流程实例"));
        definitions.add(
                new ScopeDefinition("open:user:read", "用户只读", "apex-system", "user:read", "user-org", "读取用户与组织基础信息"));
        definitions.add(new ScopeDefinition(
                "open:message:send", "站内信发送", "apex-office", "office:message:send", "office-message", "发送站内信与办公消息"));
        return List.copyOf(definitions);
    }
}
