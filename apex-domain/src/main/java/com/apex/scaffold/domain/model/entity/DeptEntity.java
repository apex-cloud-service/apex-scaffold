package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 部门实体（领域层）。
 *
 * <p>树形部门结构，支撑数据权限（C3）按部门层级过滤。与基础设施层
 * {@code DeptPO} 相互转换，领域层不感知持久化细节。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public class DeptEntity {

    /** 主键 ID */
    private Long id;

    /** 父部门 ID（0 为根部门） */
    private Long parentId;

    /** 祖级列表（逗号分隔，如 0,100） */
    private String ancestors;

    /** 部门名称 */
    private String name;

    /** 显示顺序 */
    private Integer orderNum;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态（0-停用，1-正常） */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** @return 主键 ID */
    public Long getId() { return id; }

    /** @param id 主键 ID */
    public void setId(Long id) { this.id = id; }

    /** @return 父部门 ID */
    public Long getParentId() { return parentId; }

    /** @param parentId 父部门 ID */
    public void setParentId(Long parentId) { this.parentId = parentId; }

    /** @return 祖级列表 */
    public String getAncestors() { return ancestors; }

    /** @param ancestors 祖级列表 */
    public void setAncestors(String ancestors) { this.ancestors = ancestors; }

    /** @return 部门名称 */
    public String getName() { return name; }

    /** @param name 部门名称 */
    public void setName(String name) { this.name = name; }

    /** @return 显示顺序 */
    public Integer getOrderNum() { return orderNum; }

    /** @param orderNum 显示顺序 */
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }

    /** @return 负责人 */
    public String getLeader() { return leader; }

    /** @param leader 负责人 */
    public void setLeader(String leader) { this.leader = leader; }

    /** @return 联系电话 */
    public String getPhone() { return phone; }

    /** @param phone 联系电话 */
    public void setPhone(String phone) { this.phone = phone; }

    /** @return 邮箱 */
    public String getEmail() { return email; }

    /** @param email 邮箱 */
    public void setEmail(String email) { this.email = email; }

    /** @return 状态 */
    public Integer getStatus() { return status; }

    /** @param status 状态 */
    public void setStatus(Integer status) { this.status = status; }

    /** @return 创建时间 */
    public LocalDateTime getCreateTime() { return createTime; }

    /** @param createTime 创建时间 */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
