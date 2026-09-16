package com.apex.scaffold.domain.office;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * 办公平台成员值对象
 *
 * <p>表示从外部办公平台（飞书、钉钉、企业微信）拉取的成员信息。
 * 这是一个值对象，用于在同步过程中传递成员数据，不直接持久化。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不可变对象，使用 @Value 注解</li>
 *   <li>包含外部平台的用户标识和基本信息</li>
 *   <li>支持成员所属多个部门（deptIds）</li>
 *   <li>记录成员状态，区分在职、离职等</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Value
@Builder
public class OfficeMember {
    /** 外部用户标识 */
    String externalUserId;

    /** 用户名称 */
    String name;

    /** 手机号（可能为空） */
    String mobile;

    /** 邮箱（可能为空） */
    String email;

    /** 头像 URL */
    String avatar;

    /** 所属部门 ID 列表 */
    List<String> deptIds;

    /** 用户状态（ACTIVE-在职，INACTIVE-离职） */
    String status;

    /** 职位 */
    String position;

    /** 工号 */
    String jobNumber;

    /** 成员创建时间（来自外部平台） */
    LocalDateTime createdAt;

    /** 成员更新时间（来自外部平台） */
    LocalDateTime updatedAt;

    /**
     * 判断成员是否在职
     *
     * @return 如果成员在职返回 true
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * 判断成员是否属于指定部门
     *
     * @param deptId 部门 ID
     * @return 如果成员属于该部门返回 true
     */
    public boolean belongsToDept(String deptId) {
        return deptIds != null && deptIds.contains(deptId);
    }

    /**
     * 获取主部门 ID（第一个部门）
     *
     * @return 主部门 ID，如果没有部门返回 null
     */
    public String getPrimaryDeptId() {
        return deptIds != null && !deptIds.isEmpty() ? deptIds.get(0) : null;
    }
}
