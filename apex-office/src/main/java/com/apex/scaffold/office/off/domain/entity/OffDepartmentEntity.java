package com.apex.scaffold.office.off.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 部门实体
 *
 * <p>用于表示组织架构中的部门信息，支持树形层级结构和外部系统同步。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffDepartmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String deptCode;
    private String deptName;
    private Long parentId;
    private String deptPath;
    private Integer deptLevel;
    private Integer sortOrder;
    private Long leaderId;
    private String leaderName;
    private String phone;
    private String email;
    private String status;
    private String syncSource;
    private String externalId;
    private String tenantId;

    public OffDepartmentEntity() {
        super();
    }

    /**
     * 设置部门编码，并刷新实体更新时间。
     *
     * @param deptCode 部门的唯一业务编码
     */
    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
        touch();
    }

    /**
     * 设置部门名称，并刷新实体更新时间。
     *
     * @param deptName 部门的显示名称
     */
    public void setDeptName(String deptName) {
        this.deptName = deptName;
        touch();
    }

    /**
     * 设置父部门 ID，并刷新实体更新时间。
     *
     * @param parentId 上级部门的主键 ID，顶级部门为空
     */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        touch();
    }

    /**
     * 设置部门层级路径，并刷新实体更新时间。
     *
     * @param deptPath 标识部门树形层级的路径字符串
     */
    public void setDeptPath(String deptPath) {
        this.deptPath = deptPath;
        touch();
    }

    /**
     * 设置部门层级深度，并刷新实体更新时间。
     *
     * @param deptLevel 部门在组织树中的层级（从 1 开始）
     */
    public void setDeptLevel(Integer deptLevel) {
        this.deptLevel = deptLevel;
        touch();
    }

    /**
     * 设置部门排序号，并刷新实体更新时间。
     *
     * @param sortOrder 同级部门间的展示排序值
     */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        touch();
    }

    /**
     * 设置部门负责人 ID，并刷新实体更新时间。
     *
     * @param leaderId 部门负责人的用户 ID
     */
    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
        touch();
    }

    /**
     * 设置部门负责人姓名，并刷新实体更新时间。
     *
     * @param leaderName 部门负责人的姓名
     */
    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
        touch();
    }

    /**
     * 设置部门联系电话，并刷新实体更新时间。
     *
     * @param phone 部门的对外联系电话
     */
    public void setPhone(String phone) {
        this.phone = phone;
        touch();
    }

    /**
     * 设置部门联系邮箱，并刷新实体更新时间。
     *
     * @param email 部门的对外联系邮箱
     */
    public void setEmail(String email) {
        this.email = email;
        touch();
    }

    /**
     * 设置部门状态，并刷新实体更新时间。
     *
     * @param status 部门的启用/禁用状态标识
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置同步来源，并刷新实体更新时间。
     *
     * @param syncSource 数据同步的外部平台标识（如 feishu、dingtalk、wechat）
     */
    public void setSyncSource(String syncSource) {
        this.syncSource = syncSource;
        touch();
    }

    /**
     * 设置外部系统中的部门 ID，并刷新实体更新时间。
     *
     * @param externalId 外部办公平台侧的部门 ID，与 syncSource 组合唯一标识外部部门
     */
    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 部门所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
