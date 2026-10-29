package com.apex.scaffold.office.off.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公员工持久化对象
 *
 * <p>对应数据库表 {@code off_employee}，存储从第三方办公平台（飞书、钉钉、企业微信）同步的员工信息。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #employeeCode} - 员工工号，企业内唯一标识</li>
 *   <li>{@link #employeeName} - 员工姓名</li>
 *   <li>{@link #deptId} - 所属部门 ID（关联 {@code off_department}）</li>
 *   <li>{@link #deptName} - 所属部门名称（冗余字段，便于查询）</li>
 *   <li>{@link #position} - 职位</li>
 *   <li>{@link #jobTitle} - 职级/职称</li>
 *   <li>{@link #mobile} - 手机号</li>
 *   <li>{@link #email} - 邮箱</li>
 *   <li>{@link #gender} - 性别（MALE=男、FEMALE=女、UNKNOWN=未知）</li>
 *   <li>{@link #birthday} - 生日</li>
 *   <li>{@link #hireDate} - 入职日期</li>
 *   <li>{@link #status} - 员工状态（ACTIVE=在职、RESIGNED=离职、SUSPENDED=停职）</li>
 *   <li>{@link #userId} - 关联的系统用户 ID（可选，用于打通账号体系）</li>
 *   <li>{@link #syncSource} - 同步来源平台（FEISHU=飞书、DINGTALK=钉钉、WECOM=企业微信）</li>
 *   <li>{@link #externalId} - 外部平台员工 ID（第三方平台的唯一标识）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>组织架构同步：从第三方平台定期同步员工信息</li>
 *   <li>员工查询：按部门、状态、关键字查询员工</li>
 *   <li>账号关联：将第三方员工与系统用户账号关联</li>
 *   <li>离职处理：同步员工离职状态</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>飞书（Lark）：通过飞书 API 同步员工通讯录</li>
 *   <li>钉钉（DingTalk）：通过钉钉 API 同步员工信息</li>
 *   <li>企业微信（WeChat Work）：通过企业微信 API 同步成员信息</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>员工工号 {@code employeeCode} 在租户内应保持唯一</li>
 *   <li>外部平台 ID {@code externalId} 用于数据同步的幂等性保证</li>
 *   <li>租户隔离通过 {@code tenantId} 字段实现</li>
 *   <li>逻辑删除：离职员工不物理删除，通过 {@code status} 标记</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see OffDepartmentPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_employee")
public class OffEmployeePO extends BasePO {

    @TableField("employee_code")
    private String employeeCode;

    @TableField("employee_name")
    private String employeeName;

    @TableField("dept_id")
    private Long deptId;

    @TableField("dept_name")
    private String deptName;

    @TableField("position")
    private String position;

    @TableField("job_title")
    private String jobTitle;

    @TableField("mobile")
    private String mobile;

    @TableField("email")
    private String email;

    @TableField("gender")
    private String gender;

    @TableField("birthday")
    private LocalDate birthday;

    @TableField("hire_date")
    private LocalDate hireDate;

    @TableField("status")
    private String status;

    @TableField("user_id")
    private Long userId;

    @TableField("sync_source")
    private String syncSource;

    @TableField("external_id")
    private String externalId;

    @TableField("tenant_id")
    private String tenantId;
}
