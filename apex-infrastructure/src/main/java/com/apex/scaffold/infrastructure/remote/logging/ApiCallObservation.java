package com.apex.scaffold.infrastructure.remote.logging;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 出站 HTTP 调用观测数据，不包含业务实体和数据库标识。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
public class ApiCallObservation {
    private String serviceName;
    private String method;
    private String path;
    private String url;
    private String requestHeaders;
    private String requestBody;
    private String responseStatus;
    private String responseHeaders;
    private String responseBody;
    private Long executionTime;
    private Integer status;
    private String errorMsg;
    private String traceId;
    private String requestId;
    private LocalDateTime callTime;
}
