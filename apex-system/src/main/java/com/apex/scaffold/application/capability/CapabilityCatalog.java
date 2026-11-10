package com.apex.scaffold.application.capability;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
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
     * @param owner         能力负责人
     * @param version       能力目录版本
     * @param consumers     已登记消费方
     * @param slo           服务等级目标
     * @param lifecycle     生命周期状态
     * @param openScopes    对外可申请的开放 scope（无则不对外开放）
     * @param description   能力说明
     */
    @Schema(name = "Capability", description = "能力目录条目（平台级登记，展示顺序 = 登记顺序）")
    public record Capability(
            @Schema(description = "能力编码（与套餐 features 取值一致）", example = "file-storage") String code,
            @Schema(description = "能力名称", example = "文件存储") String name,
            @Schema(description = "归属能力中心", example = "文件中心") String center,
            @Schema(description = "提供该能力的组件（Maven 模块名）", example = "apex-file-service") String component,
            @Schema(description = "能力负责人") String owner,
            @Schema(description = "能力目录版本") String version,
            @Schema(description = "已登记消费方") Set<String> consumers,
            @Schema(description = "服务等级目标") String slo,
            @Schema(description = "生命周期状态") String lifecycle,
            @Schema(description = "对外可申请的开放 scope（空集合 = 不对外开放）") Set<String> openScopes,
            @Schema(description = "能力说明") String description) {}

    /** 能力目录（插入顺序稳定，便于文档与前端展示）。 */
    private static final Map<String, Capability> CATALOG = buildCatalog();

    /** 工具类不允许实例化。 */
    private CapabilityCatalog() {}

    /**
     * 构建不可变能力目录。
     *
     * <p>使用 {@link LinkedHashMap} 保留登记顺序，最后包装为不可修改视图，避免调用方
     * 在运行期篡改目录内容或改变目录页、前端下拉框与计量报表的展示顺序。
     *
     * @return 按登记顺序保存的能力目录
     */
    private static Map<String, Capability> buildCatalog() {
        Map<String, Capability> catalog = new LinkedHashMap<>();
        register(
                catalog,
                new Capability(
                        "identity-auth",
                        "统一认证与授权",
                        "认证授权中心",
                        "apex-auth",
                        "认证授权负责人",
                        "1.0.0",
                        Set.of("apex-gateway", "apex-auth-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of("open:user:read"),
                        "登录、令牌签发/轮换/撤销、OAuth2 授权、consent、MFA"));
        register(
                catalog,
                new Capability(
                        "user-org",
                        "用户与组织",
                        "用户组织中心",
                        "apex-system",
                        "用户组织负责人",
                        "1.0.0",
                        Set.of("apex-system-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of("open:user:read"),
                        "用户、部门、岗位、角色与菜单权限"));
        register(
                catalog,
                new Capability(
                        "workflow",
                        "工作流",
                        "工作流中心",
                        "apex-workflow",
                        "工作流负责人",
                        "1.0.0",
                        Set.of("apex-workflow-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of("open:workflow:read", "open:workflow:write"),
                        "DAG 流程定义、实例推进、任务审批"));
        register(
                catalog,
                new Capability(
                        "file-storage",
                        "文件与对象存储",
                        "文件中心",
                        "apex-file",
                        "文件中心负责人",
                        "1.0.0",
                        Set.of("apex-file-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of("open:file:read", "open:file:write"),
                        "上传/分片/下载、分享、五后端存储"));
        register(
                catalog,
                new Capability(
                        "office-message",
                        "统一消息与协同",
                        "消息中心",
                        "apex-office",
                        "消息中心负责人",
                        "1.0.0",
                        Set.of("apex-office-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of("open:message:send"),
                        "站内信、办公平台 IM、通知模板与投递"));
        register(
                catalog,
                new Capability(
                        "tenant-governance",
                        "租户治理",
                        "租户治理中心",
                        "apex-system",
                        "租户治理负责人",
                        "1.0.0",
                        Set.of("apex-system-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of(),
                        "租户目录/生命周期/套餐/配额/租户级配置"));
        register(
                catalog,
                new Capability(
                        "integration-connector",
                        "系统集成连接器",
                        "集成中心",
                        "apex-integration",
                        "集成中心负责人",
                        "1.0.0",
                        Set.of("apex-integration-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of(),
                        "外部系统连接器、凭据、出站重试、入站 webhook"));
        register(
                catalog,
                new Capability(
                        "iot-device",
                        "物联网设备接入",
                        "集成中心",
                        "apex-integration",
                        "集成中心负责人",
                        "1.0.0",
                        Set.of("apex-integration-service"),
                        "99.9% 可用性",
                        "active",
                        Set.of(),
                        "设备注册/认证、遥测上行、命令下行、规则引擎"));
        // LinkedHashMap 保序包装：all()/目录页的展示顺序 = 登记顺序（Map.copyOf 不保序）
        return Collections.unmodifiableMap(catalog);
    }

    /**
     * 登记一项能力并拒绝重复编码。
     *
     * @param catalog    待写入的能力目录
     * @param capability 能力定义
     * @throws IllegalStateException 当目录中已存在相同能力编码时抛出
     */
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
     * 判断某能力是否被订阅（仅按集合判定）。
     *
     * <p>空集合表示未订阅任何能力；全量订阅必须显式表达，不能由空集合兜底，
     * 避免套餐配置缺失被静默放大为全开。
     *
     * @param features      租户套餐声明的能力集合（可空；空 = 未订阅任何能力）
     * @param capabilityCode 能力编码
     * @return 是否已订阅
     */
    public static boolean subscribed(java.util.Collection<String> features, String capabilityCode) {
        return features != null && features.contains(capabilityCode);
    }
}
