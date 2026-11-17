package com.apex.scaffold.office.off.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工信息数据传输对象
 *
 * <p>用于接口层返回员工的基本信息和组织关系，支持从飞书、钉钉、企业微信等第三方办公平台同步员工数据。
 * 员工与系统用户账号通过 userId 关联，一个员工对应一个用户账号。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #id} - 员工ID</li>
 *   <li>{@link #employeeCode} - 员工编号（工号，唯一标识）</li>
 *   <li>{@link #employeeName} - 员工姓名</li>
 *   <li>{@link #deptId} - 所属部门ID</li>
 *   <li>{@link #deptName} - 所属部门名称</li>
 *   <li>{@link #position} - 职位（如"高级工程师"）</li>
 *   <li>{@link #jobTitle} - 职称（如"工程师"、"高级工程师"）</li>
 *   <li>{@link #mobile} - 手机号码</li>
 *   <li>{@link #email} - 邮箱地址</li>
 *   <li>{@link #userId} - 关联的用户账号ID</li>
 *   <li>{@link #syncSource} - 同步来源（FEISHU、DINGTALK、WECHAT_WORK）</li>
 *   <li>{@link #externalId} - 外部系统员工ID</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>员工花名册：展示企业所有员工信息</li>
 *   <li>组织架构：按部门展示员工归属</li>
 *   <li>人员选择：审批流程、任务分配时选择人员</li>
 *   <li>权限分配：为员工分配角色和权限</li>
 *   <li>第三方同步：从办公平台同步员工档案</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>员工是企业组织架构的一员，包含工号、部门、职位等信息</li>
 *   <li>用户是系统登录账号，包含用户名、密码、权限等信息</li>
 *   <li>一个员工对应一个用户账号（通过 userId 关联）</li>
 *   <li>员工禁用时，对应的用户账号也应禁用</li>
 *   <li>员工离职时，不删除记录，仅标记为离职状态</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>syncSource 标识数据来源（FEISHU、DINGTALK、WECHAT_WORK、MANUAL）</li>
 *   <li>externalId 存储第三方平台的员工ID，用于增量同步和身份映射</li>
 *   <li>同步时保持员工的部门归属、职位、联系方式等信息</li>
 *   <li>手机号和邮箱用于与用户账号的关联</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>员工编号在租户内必须唯一</li>
 *   <li>手机号和邮箱建议唯一，但允许为空</li>
 *   <li>员工必须归属于某个部门（deptId 不能为空）</li>
 *   <li>员工姓名、员工编号为必填字段</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>员工编号（工号）是员工的唯一标识，不可修改</li>
 *   <li>员工调整部门时需更新 deptId 和 deptName</li>
 *   <li>员工离职应设置状态为"离职"，不删除记录</li>
 *   <li>禁用员工时应同步禁用对应的用户账号</li>
 *   <li>从第三方平台同步时，externalId 用于增量更新和去重</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see com.apex.scaffold.office.off.application.dto.DepartmentDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "员工信息DTO")
public class EmployeeDTO {

    @Schema(description = "员工ID")
    private Long id;

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String employeeCode;

    @Schema(description = "员工姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String employeeName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "职位")
    private String position;

    @Schema(description = "职称")
    private String jobTitle;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "入职日期")
    private LocalDate hireDate;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "同步来源")
    private String syncSource;

    @Schema(description = "外部系统ID")
    private String externalId;
}
