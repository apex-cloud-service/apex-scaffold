package com.apex.scaffold.domain.model.record;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审计日志记录（领域层查询模型）。
 *
 * <p>操作审计日志（{@code sys_operation_log} 表）的领域查询载体，由基础设施层
 * 从 PO 转换而来，供应用层查询服务转换为展示 DTO，避免 PO 跨层暴露。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Data
public class AuditLogRecord {

    /** 主键 ID */
    private Long id;

    /** 操作人 ID */
    private String operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作人 IP */
    private String operatorIp;

    /** 操作模块 */
    private String module;

    /** 操作类型 */
    private String operationType;

    /** 操作描述 */
    private String description;

    /** 请求方法 */
    private String method;

    /** 请求 URL */
    private String requestUrl;

    /** 请求参数（JSON） */
    private String requestParams;

    /** 响应结果（JSON） */
    private String responseResult;

    /** 耗时（毫秒） */
    private Long duration;

    /** 状态（SUCCESS/FAILED） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
