package com.apex.scaffold.office.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * 办公平台部门值对象
 *
 * <p>表示从外部办公平台（飞书、钉钉、企业微信）拉取的部门信息。
 * 这是一个值对象，用于在同步过程中传递部门数据，不直接持久化。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不可变对象，使用 @Value 注解</li>
 *   <li>包含外部平台的部门标识和层级信息</li>
 *   <li>支持部门树形结构（parentId）</li>
 *   <li>记录部门成员数量，用于同步校验</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Value
@Builder
public class OfficeDepartment {
    /** 外部部门标识 */
    String externalDeptId;

    /** 部门名称 */
    String name;

    /** 父部门标识（根部门为 null 或 "0"） */
    String parentId;

    /** 部门排序（用于显示顺序） */
    Integer order;

    /** 部门成员数量 */
    Integer memberCount;

    /** 部门创建时间（来自外部平台） */
    LocalDateTime createdAt;

    /** 部门更新时间（来自外部平台） */
    LocalDateTime updatedAt;

    /**
     * 判断是否为根部门
     *
     * @return 如果是根部门返回 true
     */
    public boolean isRoot() {
        return parentId == null || "0".equals(parentId) || "".equals(parentId);
    }

    /**
     * 获取部门层级深度（根部门为 0）
     *
     * @param allDepartments 所有部门列表
     * @return 层级深度
     */
    public int getDepth(java.util.List<OfficeDepartment> allDepartments) {
        if (isRoot()) {
            return 0;
        }
        return allDepartments.stream()
                .filter(d -> d.getExternalDeptId().equals(this.parentId))
                .findFirst()
                .map(parent -> parent.getDepth(allDepartments) + 1)
                .orElse(0);
    }
}
