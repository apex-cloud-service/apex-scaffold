package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;

/**
 * 部门持久化对象（sys_dept）。
 *
 * <p>树形部门结构，{@code parent_id} 自关联、{@code ancestors} 存祖级链，
 * 支撑数据权限（C3）按部门层级过滤。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@TableName("sys_dept")
public class DeptPO {

    @TableField("tenant_id")
    private String tenantId;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父部门 ID（0 为根部门） */
    @TableField("parent_id")
    private Long parentId;

    /** 祖级列表（逗号分隔，如 0,100） */
    private String ancestors;

    /** 部门名称 */
    private String name;

    /** 显示顺序 */
    @TableField("order_num")
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
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 版本号（乐观锁） */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;

    /** 逻辑删除（0-未删，1-已删） */
    @TableLogic
    private Integer deleted;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** @return 主键 ID */
    public Long getId() {
        return id;
    }

    /** @param id 主键 ID */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 父部门 ID */
    public Long getParentId() {
        return parentId;
    }

    /** @param parentId 父部门 ID */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    /** @return 祖级列表 */
    public String getAncestors() {
        return ancestors;
    }

    /** @param ancestors 祖级列表 */
    public void setAncestors(String ancestors) {
        this.ancestors = ancestors;
    }

    /** @return 部门名称 */
    public String getName() {
        return name;
    }

    /** @param name 部门名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** @return 显示顺序 */
    public Integer getOrderNum() {
        return orderNum;
    }

    /** @param orderNum 显示顺序 */
    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    /** @return 负责人 */
    public String getLeader() {
        return leader;
    }

    /** @param leader 负责人 */
    public void setLeader(String leader) {
        this.leader = leader;
    }

    /** @return 联系电话 */
    public String getPhone() {
        return phone;
    }

    /** @param phone 联系电话 */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /** @return 邮箱 */
    public String getEmail() {
        return email;
    }

    /** @param email 邮箱 */
    public void setEmail(String email) {
        this.email = email;
    }

    /** @return 状态 */
    public Integer getStatus() {
        return status;
    }

    /** @param status 状态 */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /** @return 创建时间 */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /** @param createTime 创建时间 */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /** @return 更新时间 */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /** @param updateTime 更新时间 */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /** @return 版本号 */
    public Integer getVersion() {
        return version;
    }

    /** @param version 版本号 */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /** @return 逻辑删除标记 */
    public Integer getDeleted() {
        return deleted;
    }

    /** @param deleted 逻辑删除标记 */
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
