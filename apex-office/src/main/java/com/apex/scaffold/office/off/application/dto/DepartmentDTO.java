package com.apex.scaffold.office.off.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门信息数据传输对象
 *
 * <p>用于接口层返回部门的组织架构信息，支持从飞书、钉钉、企业微信等第三方平台同步部门数据。
 * 部门采用树形结构组织，通过父部门ID和部门路径实现层级关系。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #id} - 部门ID</li>
 *   <li>{@link #deptCode} - 部门编码（唯一标识）</li>
 *   <li>{@link #deptName} - 部门名称</li>
 *   <li>{@link #parentId} - 上级部门ID（根部门为0）</li>
 *   <li>{@link #deptPath} - 部门层级路径（如 /1/10/100/）</li>
 *   <li>{@link #deptLevel} - 部门层级（从1开始）</li>
 *   <li>{@link #leaderId} - 部门负责人ID</li>
 *   <li>{@link #syncSource} - 同步来源（FEISHU、DINGTALK、WECHAT_WORK）</li>
 *   <li>{@link #externalId} - 外部系统部门ID</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>组织架构展示：前端构建部门树形结构</li>
 *   <li>部门管理：查看、编辑部门信息</li>
 *   <li>员工归属：员工关联到具体部门</li>
 *   <li>权限管理：基于部门的数据权限控制</li>
 *   <li>第三方同步：从办公平台同步部门数据</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>使用 parentId 构建父子关系</li>
 *   <li>deptPath 存储完整层级路径，便于查询所有祖先部门</li>
 *   <li>deptLevel 记录层级深度，便于层级限制和展示</li>
 *   <li>sortOrder 控制同级部门的显示顺序</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>syncSource 标识数据来源（FEISHU、DINGTALK、WECHAT_WORK、MANUAL）</li>
 *   <li>externalId 存储第三方平台的部门ID，用于增量同步和数据映射</li>
 *   <li>同步时保持部门层级关系和负责人信息</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>部门编码在租户内必须唯一</li>
 *   <li>删除部门需先检查是否有子部门和员工</li>
 *   <li>修改父部门时需重新计算 deptPath 和 deptLevel</li>
 *   <li>根部门的 parentId 固定为 0</li>
 *   <li>部门路径格式：/祖先ID/父ID/当前ID/</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see com.apex.scaffold.application.dto.EmployeeDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "部门信息DTO")
public class DepartmentDTO {

    @Schema(description = "部门ID")
    private Long id;

    @Schema(description = "部门编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptCode;

    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deptName;

    @Schema(description = "上级部门ID")
    private Long parentId;

    @Schema(description = "部门路径")
    private String deptPath;

    @Schema(description = "部门层级")
    private Integer deptLevel;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "负责人ID")
    private Long leaderId;

    @Schema(description = "负责人姓名")
    private String leaderName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "同步来源")
    private String syncSource;

    @Schema(description = "外部系统ID")
    private String externalId;
}
