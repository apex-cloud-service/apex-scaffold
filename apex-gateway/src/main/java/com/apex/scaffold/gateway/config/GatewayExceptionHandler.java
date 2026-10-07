package com.apex.scaffold.gateway.config;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局异常处理.
 *
 * <p>把网关层抛出的异常（下游不可达、路由失败、超时等）统一转换为
 * {@link Result} 结构，保证前端拿到的错误格式与业务服务一致。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@Order(-1)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        ResultCode code;
        HttpStatus status;

        if (ex instanceof ResponseStatusException statusException) {
            status = HttpStatus.resolve(statusException.getStatusCode().value());
            status = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
            code = status == HttpStatus.NOT_FOUND ? ResultCode.NOT_FOUND : ResultCode.ERROR;
        } else {
            // 下游服务不可达 / 连接被拒绝 / 读超时
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = ResultCode.SERVICE_UNAVAILABLE;
        }

        log.error("网关转发异常: path={}, status={}, cause={}", path, status.value(), ex.getMessage(), ex);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = JsonUtil.toJson(Result.error(code)).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
