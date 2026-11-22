package com.apex.scaffold.common.result;

import com.apex.scaffold.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 内部端点（{@code /internal/v1/**}）错误响应映射。
 *
 * <p>内部端点的错误语义与对外业务接口不同：只有安全类失败映射为对应 HTTP 状态，
 * 其余业务失败保持 HTTP 200 并在 {@code Result} 中携带业务码，便于集群内调用方
 * 统一解析。当前口径：
 *
 * <ul>
 *   <li>{@code 400} / {@code 40001} → HTTP 400</li>
 *   <li>{@code 401} → HTTP 401</li>
 *   <li>{@code 403} → HTTP 403</li>
 *   <li>其余业务码 → HTTP 200 + 原业务码与消息</li>
 *   <li>非 {@link BusinessException} → HTTP 500 + {@link ResultCode#ERROR}</li>
 * </ul>
 *
 * <p>注意：本映射与对外接口使用的 {@code HttpStatusUtil} 不等价（后者未知业务码映射 500），
 * 不得互换；各内部端点若需不同口径应各自实现，不要在此追加分域开关。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
public final class InternalEndpointErrors {

    /**
     * 私有构造，禁止实例化。
     *
     * @throws UnsupportedOperationException 调用时始终抛出
     */
    private InternalEndpointErrors() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 将内部端点异常映射为响应。
     *
     * @param error 异常
     * @param failureMessage 非业务异常时记录的错误日志文案
     * @return 错误响应
     */
    public static Mono<ServerResponse> toResponse(Throwable error, String failureMessage) {
        if (error instanceof BusinessException businessException) {
            int code = businessException.getCode() == null ? ResultCode.ERROR.getCode() : businessException.getCode();
            HttpStatus status =
                    switch (code) {
                        case 400, 40001 -> HttpStatus.BAD_REQUEST;
                        case 401 -> HttpStatus.UNAUTHORIZED;
                        case 403 -> HttpStatus.FORBIDDEN;
                        default -> HttpStatus.OK;
                    };
            return ServerResponse.status(status).bodyValue(Result.error(code, businessException.getMsg()));
        }
        log.error(failureMessage, error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Result.error(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg()));
    }
}
