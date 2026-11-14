package com.apex.scaffold.gateway.support;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一响应工具.
 *
 * <p>所有网关过滤器在需要终止请求时（401 / 403 / 429 等），统一经此类写出
 * 与下游单体一致的标准 {@link Result} JSON，保证契约逐字段一致（方案 §17 验收）。
 *
 * <p>典型用法：
 * <pre>{@code
 * if (非法) {
 *     return GatewayWebUtil.writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
 * }
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class GatewayWebUtil {

    /** 私有构造函数，防止实例化 */
    private GatewayWebUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 以统一 {@link Result} 结构写出错误响应.
     *
     * <p>依次设置 HTTP 状态码、{@code application/json} 内容类型，
     * 并将 {@link Result#error(ResultCode)} 序列化为响应体。
     *
     * @param exchange 当前交换对象
     * @param status   HTTP 状态码（401 / 403 / 429 等）
     * @param code     业务错误码（见 {@link ResultCode}）
     */
    public static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, ResultCode code) {
        return writeError(exchange, status, Result.error(code));
    }

    /**
     * 以统一 {@link Result} 结构写出错误响应（可指定 traceId）.
     *
     * @param exchange 当前交换对象
     * @param status   HTTP 状态码
     * @param code     业务错误码
     * @param traceId  链路追踪 ID
     */
    public static Mono<Void> writeError(
            ServerWebExchange exchange, HttpStatus status, ResultCode code, String traceId) {
        return writeError(exchange, status, Result.error(code, traceId));
    }

    /**
     * 写出任意给定的 {@link Result} 作为响应体.
     *
     * @param exchange 当前交换对象
     * @param status   HTTP 状态码
     * @param result   待序列化的结果对象
     */
    public static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, Result<?> result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = JsonUtil.toJson(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 提取客户端真实 IP.
     *
     * <p>优先读取 {@code X-Forwarded-For} 首段，其次取 {@code X-Real-IP}，
     * 最后回退到 socket 远端地址，用于限流与日志中的客户端标识。
     *
     * @param exchange 当前交换对象
     * @return 客户端 IP 字符串
     */
    public static String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
