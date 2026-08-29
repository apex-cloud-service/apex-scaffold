package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.enums.DataScopeType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据权限过滤条件构建器。
 *
 * <p>根据 {@link DataScopeType} 与 {@link DataScopeConfig} 生成纯文本 SQL 条件片段，
 * 供 {@link DataScopeSqlRewriter} 注入查询：</p>
 *
 * <ul>
 *   <li>{@link DataScopeType#ALL} → 不生成条件（返回 {@code null}）；</li>
 *   <li>{@link DataScopeType#DEPT} → {@code [alias.]dept_id = deptId}；</li>
 *   <li>{@link DataScopeType#DEPT_AND_CHILD} → {@code [alias.]dept_id IN (deptId, 子部门...)}；</li>
 *   <li>{@link DataScopeType#SELF} → {@code [alias.]user_id = userId}；</li>
 *   <li>{@link DataScopeType#CUSTOM} → {@code [alias.]dept_id IN (指定部门集合)}。</li>
 * </ul>
 *
 * <p>所有注入值均为服务端强类型数字（{@link Long}），不存在 SQL 注入风险；
 * 条件缺失关键值（如 DEPT 无 deptId）时返回 {@code null}，调用方放行不注入。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
final class DataScopeConditionBuilder {

    /** 私有构造，禁止实例化 */
    private DataScopeConditionBuilder() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 构建数据权限过滤条件。
     *
     * @param config 注入配置（{@code @DataScope} 注解转换而来）
     * @param scope  数据权限信息
     * @return SQL 条件片段（不带 {@code WHERE}/{@code AND} 前缀）；无需过滤时返回 {@code null}
     */
    static String build(DataScopeConfig config, DataScopeInfo scope) {
        if (scope == null || config == null || scope.isAll()) {
            return null;
        }
        return switch (scope.type()) {
            case DEPT -> buildDeptCondition(config, scope);
            case DEPT_AND_CHILD -> buildDeptInCondition(config, scope.effectiveDeptIds());
            case SELF -> buildSelfCondition(config, scope);
            case CUSTOM -> buildDeptInCondition(config, scope.effectiveDeptIds());
            default -> null;
        };
    }

    /**
     * 本部门数据权限：{@code [alias.]dept_id = deptId}。
     */
    private static String buildDeptCondition(DataScopeConfig config, DataScopeInfo scope) {
        Long deptId = scope.deptId();
        if (deptId == null) {
            return null;
        }
        return column(config.deptAlias(), config.deptField()) + " = " + deptId;
    }

    /**
     * 本部门及以下 / 自定义数据权限：{@code [alias.]dept_id IN (id1, id2, ...)}。
     */
    private static String buildDeptInCondition(DataScopeConfig config, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return null;
        }
        String ids = deptIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        if (ids.isEmpty()) {
            return null;
        }
        return column(config.deptAlias(), config.deptField()) + " IN (" + ids + ")";
    }

    /**
     * 仅本人数据权限：{@code [alias.]user_id = userId}。
     */
    private static String buildSelfCondition(DataScopeConfig config, DataScopeInfo scope) {
        Long userId = scope.userId();
        if (userId == null) {
            return null;
        }
        return column(config.userAlias(), config.userField()) + " = " + userId;
    }

    /**
     * 拼接限定列名：指定别名时带别名前缀，否则使用裸列名。
     */
    private static String column(String alias, String field) {
        if (alias != null && !alias.isBlank()) {
            return alias + "." + field;
        }
        return field;
    }
}
