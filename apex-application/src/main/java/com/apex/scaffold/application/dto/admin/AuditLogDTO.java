package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 操作审计日志 DTO。
 *
 * <p>管理后台审计日志查询的展示模型，字段与 {@code sys_operation_log} 表对齐
 * （由基础设施层 PO 转换而来，避免 PO 跨层暴露）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Data
@AllArgsConstructor
@Schema(description = "操作审计日志")
public class AuditLogDTO {

    /** 主键 ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 操作人 ID */
    @Schema(description = "操作人ID")
    private String operatorId;

    /** 操作人姓名 */
    @Schema(description = "操作人姓名")
    private String operatorName;

    /** 操作人 IP */
    @Schema(description = "操作人IP")
    private String operatorIp;

    /** 操作模块 */
    @Schema(description = "操作模块")
    private String module;

    /** 操作类型 */
    @Schema(description = "操作类型")
    private String operationType;

    /** 操作描述 */
    @Schema(description = "操作描述")
    private String description;

    /** 请求方法 */
    @Schema(description = "请求方法")
    private String method;

    /** 请求 URL */
    @Schema(description = "请求URL")
    private String requestUrl;

    /** 请求参数（JSON） */
    @Schema(description = "请求参数（JSON）")
    private String requestParams;

    /** 响应结果（JSON） */
    @Schema(description = "响应结果（JSON）")
    private String responseResult;

    /** 耗时（毫秒） */
    @Schema(description = "耗时（毫秒）")
    private Long duration;

    /** 状态（SUCCESS/FAILED） */
    @Schema(description = "状态（SUCCESS/FAILED）")
    private String status;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 无参构造（JSON 反序列化） */
    public AuditLogDTO() {}
}
