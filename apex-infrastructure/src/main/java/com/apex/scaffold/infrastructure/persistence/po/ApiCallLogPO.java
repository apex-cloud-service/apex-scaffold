package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API 调用日志持久化对象。
 *
 * <p>映射数据库表 {@code sys_api_call_log}，记录对外部 HTTP 服务的每次调用详情。
 * 继承 {@link BasePO} 获得 id / createTime / updateTime / version / deleted 通用字段。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.entity.ApiCallLogEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_api_call_log")
public class ApiCallLogPO extends BasePO {

    /** 序列化版本号。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标服务名称。 */
    @TableField("service_name")
    private String serviceName;

    /** HTTP 请求方法。 */
    @TableField("method")
    private String method;

    /** 请求路径（不含 host）。 */
    @TableField("path")
    private String path;

    /** 完整请求 URL。 */
    @TableField("url")
    private String url;

    /** 请求头（JSON 格式，已脱敏）。 */
    @TableField("request_headers")
    private String requestHeaders;

    /** 请求体（可能截断）。 */
    @TableField("request_body")
    private String requestBody;

    /** HTTP 响应状态码。 */
    @TableField("response_status")
    private String responseStatus;

    /** 响应头（JSON 格式）。 */
    @TableField("response_headers")
    private String responseHeaders;

    /** 响应体（可能截断）。 */
    @TableField("response_body")
    private String responseBody;

    /** 执行耗时（毫秒）。 */
    @TableField("execution_time")
    private Long executionTime;

    /** 调用状态：0-成功 1-客户端错误 2-服务端错误 3-网络异常。 */
    @TableField("status")
    private Integer status;

    /** 错误信息。 */
    @TableField("error_msg")
    private String errorMsg;

    /** 链路追踪 ID。 */
    @TableField("trace_id")
    private String traceId;

    /** 请求唯一标识（UUID）。 */
    @TableField("request_id")
    private String requestId;

    /** 调用发生时间。 */
    @TableField("call_time")
    private LocalDateTime callTime;
}
