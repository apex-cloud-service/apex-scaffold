package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 调用日志响应 DTO。
 *
 * <p>管理后台「监控中心-API调用日志」分页响应的展示元素，承载单次对外部 HTTP
 * 服务调用的概要信息（请求/响应体与敏感头不随列表返回，避免大字段拖慢列表）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API调用日志响应")
public class ApiCallLogDTO {

    /** 主键 ID（雪花算法）。 */
    @Schema(description = "日志ID")
    private Long id;

    /** 目标服务名称，如 wechat / chuanglan / baidu-image / face-analysis。 */
    @Schema(description = "目标服务名称")
    private String serviceName;

    /** HTTP 请求方法，如 GET / POST / PUT / DELETE。 */
    @Schema(description = "HTTP请求方法")
    private String method;

    /** 请求路径（不含 host）。 */
    @Schema(description = "请求路径")
    private String path;

    /** 完整请求 URL（含 query 参数，敏感参数已脱敏）。 */
    @Schema(description = "完整请求URL")
    private String url;

    /** HTTP 响应状态码，如 200 / 404 / 500。无响应时为 null。 */
    @Schema(description = "HTTP响应状态码")
    private String responseStatus;

    /** 执行耗时（毫秒）。 */
    @Schema(description = "执行耗时（毫秒）")
    private Long executionTime;

    /** 调用状态：0-成功 1-客户端错误 2-服务端错误 3-网络异常。 */
    @Schema(description = "调用状态：0-成功 1-客户端错误 2-服务端错误 3-网络异常")
    private Integer status;

    /** 错误信息（网络异常或非 2xx 时记录）。 */
    @Schema(description = "错误信息")
    private String errorMsg;

    /** 链路追踪 ID。 */
    @Schema(description = "链路追踪ID")
    private String traceId;

    /** 请求唯一标识（UUID，由 Filter 自动生成）。 */
    @Schema(description = "请求唯一标识")
    private String requestId;

    /** 调用发生时间。 */
    @Schema(description = "调用发生时间")
    private LocalDateTime callTime;

    /** 记录创建时间。 */
    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;
}
