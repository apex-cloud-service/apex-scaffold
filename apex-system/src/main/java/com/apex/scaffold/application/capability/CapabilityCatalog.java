package com.apex.scaffold.application.capability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力目录（方案 M4 / 批次 D4）。
 *
 * <p>中台"能力运营"的代码侧单一数据源：登记每个可售/可订阅能力及其归属中心、
 * 依赖组件、开放 scope 与套餐开关键。与套餐 {@code features} JSON（B3）对应：
 * 租户套餐声明 {@code features} 即订阅对应能力。
 *
 * <p>新增能力 = 在本目录登记一行，能力订阅校验与目录页自动生效（不改校验逻辑）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class CapabilityCatalog {

    /**
     * 能力定义。
     *
     * @param code          能力编码（与套餐 features 取值一致）
     * @param name          能力名称
     * @param center        归属能力中心
     * @param component     提供该能力的组件（Maven 模块名）
     * @param openScopes    对外可申请的开放 scope（无则不对外开放）
     * @param description   能力说明
     */
    public record Capability(
            String code, String name, String center, String component, Set<String> openScopes, String description) {}

    /** 能力目录（插入顺序稳定，便于文档与前端展示）。 */
    private static final Map<String, Capability> CATALOG = buildCatalog();

    private CapabilityCatalog() {}

    private static Map<String, Capability> buildCatalog() {
        Map<String, Capability> catalog = new LinkedHashMap<>();
        register(
                catalog,
                new Capability(
                        "identity-auth",
                        "统一认证与授权",
                        "认证授权中心",
                        "apex-auth",
                        Set.of("open:user:read"),
                        "登录、令牌签发/轮换/撤销、OAuth2 授权、consent、MFA"));
        register(
                catalog,
                new Capability(
                        "user-org", "用户与组织", "用户组织中心", "apex-system", Set.of("open:user:read"), "用户、部门、岗位、角色与菜单权限"));
        register(
                catalog,
                new Capability(
                        "workflow",
                        "工作流",
                        "工作流中心",
                        "apex-workflow",
                        Set.of("open:workflow:read", "open:workflow:write"),
                        "DAG 流程定义、实例推进、任务审批"));
        register(
                catalog,
                new Capability(
                        "file-storage",
                        "文件与对象存储",
                        "文件中心",
                        "apex-file",
                        Set.of("open:file:read", "open:file:write"),
                        "上传/分片/下载、分享、五后端存储"));
        register(
                catalog,
                new Capability(
                        "office-message",
                        "统一消息与协同",
                        "消息中心",
                        "apex-office",
                        Set.of("open:message:send"),
                        "站内信、办公平台 IM、通知模板与投递"));
        register(
                catalog,
                new Capability(
                        "tenant-governance", "租户治理", "租户治理中心", "apex-system", Set.of(), "租户目录/生命周期/套餐/配额/租户级配置"));
        register(
                catalog,
                new Capability(
                        "integration-connector",
                        "系统集成连接器",
                        "集成中心",
                        "apex-integration",
                        Set.of(),
                        "外部系统连接器、凭据、出站重试、入站 webhook（M5 交付）"));
        register(
                catalog,
                new Capability(
                        "iot-device",
                        "物联网设备接入",
                        "集成中心",
                        "apex-integration",
                        Set.of(),
                        "设备注册/认证、遥测上行、命令下行、规则引擎（M5 交付）"));
        return Map.copyOf(catalog);
    }

    private static void register(Map<String, Capability> catalog, Capability capability) {
        if (catalog.putIfAbsent(capability.code(), capability) != null) {
            throw new IllegalStateException("重复能力编码: " + capability.code());
        }
    }

    /**
     * 全部能力（不可变快照）。
     *
     * @return 能力列表
     */
    public static List<Capability> all() {
        return List.copyOf(CATALOG.values());
    }

    /**
     * 按编码查询能力。
     *
     * @param code 能力编码
     * @return 能力；未登记时为 null
     */
    public static Capability find(String code) {
        return CATALOG.get(code);
    }

    /**
     * 校验套餐 features 声明的能力编码是否全部已登记。
     *
     * @param features 套餐声明的能力集合（可空）
     * @return 未登记的能力编码（空表示全部合法）
     */
    public static List<String> unknownFeatures(java.util.Collection<String> features) {
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        return features.stream().filter(code -> !CATALOG.containsKey(code)).toList();
    }

    /**
     * 判断某能力是否被订阅。
     *
     * @param features      租户套餐声明的能力集合（可空；空 = 全量订阅，与套餐语义一致）
     * @param capabilityCode 能力编码
     * @return 是否已订阅
     */
    public static boolean subscribed(java.util.Collection<String> features, String capabilityCode) {
        if (features == null || features.isEmpty()) {
            return true;
        }
        return features.contains(capabilityCode);
    }
}
