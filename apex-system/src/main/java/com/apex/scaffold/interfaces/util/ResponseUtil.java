package com.apex.scaffold.interfaces.util;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HTTP JSON 响应写入工具类
 *
 * <p>提供统一的 WebFlux 环境下 JSON 格式 HTTP 响应写入方法，消除 {@code SecurityConfig}、
 * {@code JwtAuthenticationFilter} 和 {@code GlobalExceptionHandler} 中重复的响应写入代码。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #writeJson(ServerWebExchange, HttpStatus, Result)} — 核心方法，写入任意 JSON 响应</li>
 *   <li>{@link #unauthorized(ServerWebExchange, String)} — 返回 401 未授权响应</li>
 *   <li>{@link #forbidden(ServerWebExchange, String)} — 返回 403 禁止访问响应</li>
 *   <li>{@link #error(ServerWebExchange, HttpStatus, Integer, String)} — 返回自定义错误响应</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>工具类模式</b>：私有构造方法禁止实例化，全部通过静态方法调用</li>
 *   <li><b>模板方法模式</b>：所有方法内部委托给 {@link #writeJson} 执行实际的响应写入</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>所有响应统一为 JSON 格式，包含 code、msg、data、traceId 四个字段：
 * <pre>{@code * {
 *   "code": 401,
 *   "msg": "未授权，请先登录",
 *   "data": null,
 *   "traceId": "a1b2c3d4e5f6..."
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Result
 * @see ServerWebExchange
 */
public final class ResponseUtil {

    /** 私有构造函数，防止实例化 */
    private ResponseUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 写入 JSON 格式的 HTTP 响应
     *
     * <p>核心方法，将 {@link Result} 对象序列化为 JSON 字符串并写入 HTTP 响应体。
     * 自动设置 Content-Type 为 {@code application/json}。
     *
     * @param exchange 当前 WebFlux 服务端交换对象
     * @param status   HTTP 状态码
     * @param result   统一响应结果对象
     */
    public static Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, Result<?> result) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        byte[] bytes = JsonUtil.toJson(result).getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(bytes)));
    }

    /**
     * 返回 401 未授权响应
     *
     * <p>快速创建标准的未授权错误响应，适用于 JWT 认证失败或缺少 Token 的场景。
     *
     * @param exchange 当前 WebFlux 服务端交换对象
     * @param message  错误提示信息
     */
    public static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        String traceId = TraceIdUtil.getTraceId();
        Result<?> result = Result.error(ResultCode.UNAUTHORIZED.getCode(), message, traceId);
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, result);
    }

    /**
     * 返回 401 登录已过期响应（Token 过期专用）
     *
     * <p>与 {@link #unauthorized} 的区别在于使用专用错误码 40101，
     * 前端可通过此码精确识别"Token 过期需重新登录"场景，引导用户跳转到登录页。
     *
     * @param exchange 当前 WebFlux 服务端交换对象
     * @param message  错误提示信息
     * @since 2.2.0
     */
    public static Mono<Void> tokenExpired(ServerWebExchange exchange, String message) {
        String traceId = TraceIdUtil.getTraceId();
        Result<?> result = Result.error(ResultCode.TOKEN_EXPIRED.getCode(), message, traceId);
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, result);
    }

    /**
     * 返回 403 禁止访问响应
     *
     * <p>快速创建标准的禁止访问错误响应，适用于权限不足的场景。
     *
     * @param exchange 当前 WebFlux 服务端交换对象
     * @param message  错误提示信息
     */
    public static Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        String traceId = TraceIdUtil.getTraceId();
        Result<?> result = Result.error(ResultCode.FORBIDDEN.getCode(), message, traceId);
        return writeJson(exchange, HttpStatus.FORBIDDEN, result);
    }

    /**
     * 返回自定义错误响应
     *
     * <p>创建包含自定义业务错误码和 HTTP 状态码的错误响应。
     *
     * @param exchange 当前 WebFlux 服务端交换对象
     * @param status   HTTP 状态码
     * @param code     业务错误码
     * @param message  错误提示信息
     */
    public static Mono<Void> error(ServerWebExchange exchange, HttpStatus status, Integer code, String message) {
        String traceId = TraceIdUtil.getTraceId();
        Result<?> result = Result.error(code, message, traceId);
        return writeJson(exchange, status, result);
    }
}
