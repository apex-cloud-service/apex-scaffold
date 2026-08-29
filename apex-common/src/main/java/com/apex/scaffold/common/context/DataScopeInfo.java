package com.apex.scaffold.common.context;

import com.apex.scaffold.common.enums.DataScopeType;
import java.util.List;
import java.util.Objects;

/**
 * 数据权限信息（当前请求用户的数据可见范围）。
 *
 * <p>由数据权限装配端（{@code DataScopeAspect} / 认证过滤器 / 业务代码）解析当前用户后填充，
 * 供 {@code DataScopeInnerInterceptor} 在 SQL 执行前自动拼接过滤条件。</p>
 *
 * @param type    数据权限类型（{@link DataScopeType}，不允许为 {@code null}）
 * @param userId  当前用户 ID（{@link DataScopeType#SELF} 类型需要）
 * @param deptId  当前用户所属部门 ID（{@link DataScopeType#DEPT} 类型需要）
 * @param deptIds 可见部门 ID 集合（{@link DataScopeType#DEPT_AND_CHILD} 为本部门+子部门，
 *                {@link DataScopeType#CUSTOM} 为自定义指定集合）
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
public record DataScopeInfo(DataScopeType type, Long userId, Long deptId, List<Long> deptIds) {

    /**
     * 紧凑构造：规范化 {@code deptIds}（非空、不可变快照）。
     */
    public DataScopeInfo {
        Objects.requireNonNull(type, "type must not be null");
        deptIds = deptIds == null || deptIds.isEmpty() ? List.of() : List.copyOf(deptIds);
    }

    /**
     * 全部数据权限。
     *
     * @return 全部数据权限信息
     */
    public static DataScopeInfo all() {
        return new DataScopeInfo(DataScopeType.ALL, null, null, List.of());
    }

    /**
     * 本部门数据权限。
     *
     * @param deptId 当前用户所属部门 ID
     * @return 数据权限信息
     */
    public static DataScopeInfo ofDept(Long deptId) {
        return new DataScopeInfo(DataScopeType.DEPT, null, deptId, List.of());
    }

    /**
     * 本部门及以下数据权限。
     *
     * @param deptId  当前用户所属部门 ID
     * @param deptIds 可见部门集合（本部门 + 全部子部门）
     * @return 数据权限信息
     */
    public static DataScopeInfo ofDeptAndChild(Long deptId, List<Long> deptIds) {
        return new DataScopeInfo(DataScopeType.DEPT_AND_CHILD, null, deptId, deptIds);
    }

    /**
     * 仅本人数据权限。
     *
     * @param userId 当前用户 ID
     * @return 数据权限信息
     */
    public static DataScopeInfo ofSelf(Long userId) {
        return new DataScopeInfo(DataScopeType.SELF, userId, null, List.of());
    }

    /**
     * 自定义数据权限。
     *
     * @param deptIds 指定的可见部门 ID 集合
     * @return 数据权限信息
     */
    public static DataScopeInfo ofCustom(List<Long> deptIds) {
        return new DataScopeInfo(DataScopeType.CUSTOM, null, null, deptIds);
    }

    /**
     * 是否全部数据权限（拦截器无需注入条件）。
     *
     * @return {@code true} 表示全部可见
     */
    public boolean isAll() {
        return type == DataScopeType.ALL;
    }

    /**
     * 实际生效的部门 ID 集合。
     *
     * <p>{@code deptIds} 非空时直接返回；否则回退为 {@code [deptId]}。
     * 保证 {@link DataScopeType#DEPT_AND_CHILD} 与 {@link DataScopeType#CUSTOM}
     * 即使装配端只提供了 {@code deptId} 也能正确生成 {@code IN (...)} 条件。</p>
     *
     * @return 不可变的部门 ID 集合，可能为空
     */
    public List<Long> effectiveDeptIds() {
        if (!deptIds.isEmpty()) {
            return deptIds;
        }
        return deptId == null ? List.of() : List.of(deptId);
    }
}
