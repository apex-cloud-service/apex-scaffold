package com.apex.scaffold.common.util;

import java.net.InetSocketAddress;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 网络请求工具类
 *
 * <p>提供从 HTTP 请求中提取客户端 IP、User-Agent 等网络信息的通用方法。
 * 支持代理场景下的真实 IP 获取。
 *
 * <h2>IP 获取优先级</h2>
 * <ol>
 *   <li>{@code X-Forwarded-For} 请求头（代理场景）</li>
 *   <li>{@code X-Real-IP} 请求头（Nginx 代理）</li>
 *   <li>{@link ServerHttpRequest#getRemoteAddress()} 直接连接地址</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class RequestUtil {

    /** 未知IP常量 */
    private static final String UNKNOWN_IP = "unknown";

    /** 私有构造函数，防止实例化 */
    private RequestUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 获取客户端真实 IP 地址
     *
     * <p>优先从 {@code X-Forwarded-For} 请求头获取，支持多级代理分隔。
     * 若不存在则回退到 {@code X-Real-IP}，最后使用远程连接地址。
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址，无法获取时返回 {@code "unknown"}
     */
    public static String getClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return UNKNOWN_IP;
    }
}
