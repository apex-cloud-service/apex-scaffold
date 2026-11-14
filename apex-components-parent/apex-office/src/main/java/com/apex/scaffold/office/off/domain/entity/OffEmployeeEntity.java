package com.apex.scaffold.office.off.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 员工实体
 *
 * <p>用于表示组织内的员工信息，包含基本信息、部门归属、职位信息和外部系统同步标识。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Getter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffEmployeeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String employeeCode;
    private String employeeName;
    private Long deptId;
    private String deptName;
    private String position;
    private String jobTitle;
    private String mobile;
    private String email;
    private String gender;
    private LocalDate birthday;
    private LocalDate hireDate;
    private String status;
    private Long userId;
    private String syncSource;
    private String externalId;
    private String tenantId;

    /**
     * 设置工号，赋值后刷新更新时间。
     *
     * @param employeeCode 员工编号
     */
    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
        touch();
    }

    /**
     * 设置员工姓名，赋值后刷新更新时间。
     *
     * @param employeeName 员工姓名
     */
    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
        touch();
    }

    /**
     * 设置所属部门 ID，赋值后刷新更新时间。
     *
     * @param deptId 部门 ID
     */
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
        touch();
    }

    /**
     * 设置所属部门名称，赋值后刷新更新时间。
     *
     * @param deptName 部门显示名称
     */
    public void setDeptName(String deptName) {
        this.deptName = deptName;
        touch();
    }

    /**
     * 设置岗位，赋值后刷新更新时间。
     *
     * @param position 员工岗位名称
     */
    public void setPosition(String position) {
        this.position = position;
        touch();
    }

    /**
     * 设置职称，赋值后刷新更新时间。
     *
     * @param jobTitle 员工职称
     */
    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
        touch();
    }

    /**
     * 设置手机号，赋值后刷新更新时间。
     *
     * @param mobile 员工手机号码
     */
    public void setMobile(String mobile) {
        this.mobile = mobile;
        touch();
    }

    /**
     * 设置邮箱，赋值后刷新更新时间。
     *
     * @param email 员工电子邮箱
     */
    public void setEmail(String email) {
        this.email = email;
        touch();
    }

    /**
     * 设置性别，赋值后刷新更新时间。
     *
     * @param gender 员工性别
     */
    public void setGender(String gender) {
        this.gender = gender;
        touch();
    }

    /**
     * 设置出生日期，赋值后刷新更新时间。
     *
     * @param birthday 员工出生日期
     */
    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
        touch();
    }

    /**
     * 设置入职日期，赋值后刷新更新时间。
     *
     * @param hireDate 员工入职日期
     */
    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
        touch();
    }

    /**
     * 设置员工状态，赋值后刷新更新时间。
     *
     * @param status 员工在职状态标识
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置关联的系统用户 ID，赋值后刷新更新时间。
     *
     * @param userId 关联的系统用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
        touch();
    }

    /**
     * 设置同步来源，赋值后刷新更新时间。
     *
     * @param syncSource 员工数据的外部同步来源系统标识
     */
    public void setSyncSource(String syncSource) {
        this.syncSource = syncSource;
        touch();
    }

    /**
     * 设置外部系统唯一标识，赋值后刷新更新时间。
     *
     * @param externalId 员工在外部同步系统中的唯一 ID
     */
    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch();
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识，用于多租户数据隔离
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
