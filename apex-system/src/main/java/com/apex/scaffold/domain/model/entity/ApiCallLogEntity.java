package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * API 调用日志领域实体。
 *
 * <p>记录每一次对外部 HTTP 服务的调用详情，包括请求 URL、请求体、响应状态码、
 * 响应体、执行耗时和异常信息。该实体是只读的，所有字段通过持久层回填。</p>
 *
 * <h2>调用状态枚举</h2>
 * <table>
 *   <tr><th>值</th><th>含义</th></tr>
 *   <tr><td>{@code 0}</td><td>成功（HTTP 2xx）</td></tr>
 *   <tr><td>{@code 1}</td><td>客户端错误（HTTP 4xx）</td></tr>
 *   <tr><td>{@code 2}</td><td>服务端错误（HTTP 5xx）</td></tr>
 *   <tr><td>{@code 3}</td><td>网络异常（连接超时、DNS 解析失败等）</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ApiCallLogEntity extends BaseEntity {

    /** 序列化版本号。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建 API 调用日志实体对象。 */
    public ApiCallLogEntity() {}

    /** 目标服务名称，如 wechat / chuanglan / baidu-image / face-analysis。 */
    private String serviceName;

    /** HTTP 请求方法，如 GET / POST / PUT / DELETE。 */
    private String method;

    /** 请求路径（不含 host），如 /api/v1/login。 */
    private String path;

    /** 完整请求 URL（包含 query 参数）。 */
    private String url;

    /** 请求头（JSON 格式，敏感字段已脱敏）。 */
    private String requestHeaders;

    /** 请求体（超过默认限制时截断）。 */
    private String requestBody;

    /** HTTP 响应状态码，如 200 / 404 / 500。无响应时为 null。 */
    private String responseStatus;

    /** 响应头（JSON 格式）。 */
    private String responseHeaders;

    /** 响应体（超过默认限制时截断）。 */
    private String responseBody;

    /** 执行耗时（毫秒）。 */
    private Long executionTime;

    /** 调用状态：0-成功 1-客户端错误 2-服务端错误 3-网络异常。 */
    private Integer status;

    /** 错误信息（异常时记录）。 */
    private String errorMsg;

    /** 链路追踪 ID。 */
    private String traceId;

    /** 请求唯一标识（UUID，由 Filter 自动生成）。 */
    private String requestId;

    /** 调用发生时间。 */
    private LocalDateTime callTime;
}
