package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作审计日志数据传输对象
 *
 * <p>管理后台审计日志查询的展示模型，记录系统内所有重要操作的审计信息。
 * 字段与 {@code sys_operation_log} 表对齐，由基础设施层 PO 转换而来，避免 PO 跨层暴露。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>操作审计：管理员查看系统操作历史</li>
 *   <li>安全审查：排查异常操作和安全事件</li>
 *   <li>合规要求：满足审计日志保留的合规要求</li>
 *   <li>问题排查：定位系统问题的操作来源</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>数据变更：创建、更新、删除等写操作</li>
 *   <li>敏感查询：导出、批量查询等敏感读操作</li>
 *   <li>权限变更：角色、权限的授予和回收</li>
 *   <li>系统配置：系统参数、规则的修改</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 2.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "操作审计日志")
public class AuditLogDTO {

    /**
     * 主键 ID
     *
     * <p>审计日志的唯一标识。
     */
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 操作人 ID
     *
     * <p>执行操作的用户标识。
     */
    @Schema(description = "操作人ID")
    private String operatorId;

    /**
     * 操作人姓名
     *
     * <p>操作人的显示名称，用于前端展示。
     */
    @Schema(description = "操作人姓名")
    private String operatorName;

    /**
     * 操作人 IP
     *
     * <p>操作请求的来源 IP 地址，用于定位操作来源。
     *
     * <p>示例：{@code 192.168.1.100}
     */
    @Schema(description = "操作人IP地址")
    private String operatorIp;

    /**
     * 操作模块
     *
     * <p>操作所属的功能模块。
     *
     * <p>示例：{@code 用户管理}、{@code 角色管理}、{@code 系统配置}
     */
    @Schema(description = "操作模块")
    private String module;

    /**
     * 操作类型
     *
     * <p>操作的具体类型。
     *
     * <p>常见类型：
     * <ul>
     *   <li>{@code CREATE}：创建</li>
     *   <li>{@code UPDATE}：更新</li>
     *   <li>{@code DELETE}：删除</li>
     *   <li>{@code QUERY}：查询</li>
     *   <li>{@code EXPORT}：导出</li>
     * </ul>
     */
    @Schema(description = "操作类型（CREATE/UPDATE/DELETE/QUERY/EXPORT）")
    private String operationType;

    /**
     * 操作描述
     *
     * <p>对操作内容的详细描述。
     *
     * <p>示例：{@code 创建用户 zhangsan}、{@code 删除角色 ROLE_ADMIN}
     */
    @Schema(description = "操作描述")
    private String description;

    /**
     * 请求方法
     *
     * <p>HTTP 请求方法。
     *
     * <p>示例：{@code GET}、{@code POST}、{@code PUT}、{@code DELETE}
     */
    @Schema(description = "HTTP请求方法")
    private String method;

    /**
     * 请求 URL
     *
     * <p>完整的请求路径，不包含域名和端口。
     *
     * <p>示例：{@code /api/v1/users}
     */
    @Schema(description = "请求URL路径")
    private String requestUrl;

    /**
     * 请求参数（JSON）
     *
     * <p>请求携带的参数，以 JSON 格式存储。
     * 敏感字段（如密码）应在记录前脱敏。
     */
    @Schema(description = "请求参数（JSON格式，敏感字段已脱敏）")
    private String requestParams;

    /**
     * 响应结果（JSON）
     *
     * <p>接口返回的响应数据，以 JSON 格式存储。
     * 大字段可能被截断，敏感数据应脱敏。
     */
    @Schema(description = "响应结果（JSON格式）")
    private String responseResult;

    /**
     * 耗时（毫秒）
     *
     * <p>从请求到达到响应返回的总耗时。
     */
    @Schema(description = "执行耗时（毫秒）")
    private Long duration;

    /**
     * 状态
     *
     * <p>操作执行结果状态。
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code SUCCESS}：成功</li>
     *   <li>{@code FAILED}：失败</li>
     * </ul>
     */
    @Schema(description = "执行状态（SUCCESS/FAILED）")
    private String status;

    /**
     * 创建时间
     *
     * <p>审计日志记录创建的时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
