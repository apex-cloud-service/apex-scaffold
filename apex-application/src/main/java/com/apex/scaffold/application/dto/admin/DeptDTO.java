package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门 DTO（树形）。
 *
 * <p>管理后台部门管理的展示模型，{@code children} 承载子部门列表
 * （由 {@code DeptService} 按 parentId 树形组装）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Schema(description = "部门（树形）")
public class DeptDTO {

    /** 部门 ID */
    @Schema(description = "部门ID")
    private Long id;

    /** 父部门 ID */
    @Schema(description = "父部门ID")
    private Long parentId;

    /** 部门名称 */
    @Schema(description = "部门名称")
    private String name;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer orderNum;

    /** 负责人 */
    @Schema(description = "负责人")
    private String leader;

    /** 联系电话 */
    @Schema(description = "联系电话")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 状态（0-停用，1-正常） */
    @Schema(description = "状态（0-停用，1-正常）")
    private Integer status;

    @Schema(description = "乐观锁版本")
    private Integer lockVersion;

    /** 子部门列表 */
    @Schema(description = "子部门列表")
    private List<DeptDTO> children = new ArrayList<>();

    /** 无参构造 */
    public DeptDTO() {}

    /** @return 部门 ID */
    public Long getId() {
        return id;
    }

    /** @param id 部门 ID */
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

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    /** @return 子部门列表 */
    public List<DeptDTO> getChildren() {
        return children;
    }

    /** @param children 子部门列表 */
    public void setChildren(List<DeptDTO> children) {
        this.children = children;
    }
}
