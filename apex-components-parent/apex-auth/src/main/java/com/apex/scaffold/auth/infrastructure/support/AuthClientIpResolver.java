package com.apex.scaffold.auth.infrastructure.support;

import com.apex.scaffold.auth.config.AuthTrustedProxyProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * 认证域客户端 IP 安全解析器。
 *
 * <p>根据 TCP 直连地址、{@code X-Forwarded-For} 与 {@code X-Real-IP} 解析真实客户端地址。
 * 为防止客户端伪造转发头，只有当直连地址命中 {@link AuthTrustedProxyProperties#trustedProxyCidrs}
 * 配置的可信代理网段时才会采信转发头。</p>
 *
 * <p>{@code X-Forwarded-For} 按代理链从右向左检查，跳过所有可信代理，第一个非可信地址
 * 视为原始客户端地址；代理链中没有可用地址时依次回退到 {@code X-Real-IP} 和直连地址。
 * 非法地址与解析失败按无效处理，不抛出异常。</p>
 *
 * <p>解析结果用于按 IP 限流、登录风控与安全审计，可信代理网段必须与实际负载均衡器、
 * 反向代理的网络边界保持一致。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AuthTrustedProxyProperties
 */
@Component
@RequiredArgsConstructor
public class AuthClientIpResolver {

    private static final int BITS_PER_BYTE = Byte.SIZE;

    /** 无法解析客户端地址时使用的占位值。 */
    public static final String UNKNOWN_IP = "unknown";

    /** 可信代理配置。 */
    private final AuthTrustedProxyProperties properties;

    /**
     * 解析函数式请求对应的真实客户端 IP。
     *
     * @param request 函数式请求
     * @return 客户端 IP，无法解析时返回 {@link #UNKNOWN_IP}
     */
    public String resolve(ServerRequest request) {
        return resolve(
                request.remoteAddress().orElse(null),
                request.headers().header("X-Forwarded-For"),
                request.headers().firstHeader("X-Real-IP"));
    }

    /**
     * 解析请求头对应的真实客户端 IP。
     *
     * @param headers 请求头
     * @param remoteAddressSupplier 直连地址提供者，延迟到需要时求值
     * @return 客户端 IP，无法解析时返回 {@link #UNKNOWN_IP}
     */
    public String resolve(HttpHeaders headers, Supplier<InetSocketAddress> remoteAddressSupplier) {
        return resolve(remoteAddressSupplier.get(), headers.get("X-Forwarded-For"), headers.getFirst("X-Real-IP"));
    }

    /**
     * 解析真实客户端 IP。
     *
     * <p>直连地址不属于可信代理时忽略全部转发头，直接返回直连地址；属于可信代理时
     * 优先从 {@code X-Forwarded-For} 代理链中寻找最靠右的非可信地址，再回退到
     * {@code X-Real-IP}，最后回退到直连地址。</p>
     *
     * @param remoteAddress TCP 连接的远端地址，允许为 {@code null}
     * @param forwardedFor {@code X-Forwarded-For} 的全部请求头值，允许为 {@code null} 或空集合
     * @param realIp {@code X-Real-IP} 请求头值，允许为 {@code null} 或空白字符串
     * @return 规范化后的客户端 IP，无法解析时返回 {@link #UNKNOWN_IP}
     */
    public String resolve(InetSocketAddress remoteAddress, List<String> forwardedFor, String realIp) {
        String remoteIp = addressOf(remoteAddress);
        if (remoteIp == null) {
            return UNKNOWN_IP;
        }
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            String[] chain = String.join(",", forwardedFor).split(",");
            for (int i = chain.length - 1; i >= 0; i--) {
                String candidate = normalize(chain[i]);
                if (candidate != null && !isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }
        String normalizedRealIp = normalize(realIp);
        return normalizedRealIp != null ? normalizedRealIp : remoteIp;
    }

    /**
     * 从套接字地址中提取规范化的数字地址。
     *
     * @param address 远端套接字地址，允许为 {@code null} 或未解析状态
     * @return 数字形式的 IPv4/IPv6 地址；地址不可用时返回 {@code null}
     */
    private String addressOf(InetSocketAddress address) {
        return address == null || address.getAddress() == null
                ? null
                : address.getAddress().getHostAddress();
    }

    /**
     * 清理并解析单个地址文本。
     *
     * @param value 待解析的地址文本，允许为 {@code null} 或空白字符串
     * @return 规范化后的 IPv4/IPv6 地址；非法地址返回 {@code null}
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // 仅接受 IP 字面量：getByName 会触发 DNS 解析，可能把主机名（如 not-an-ip）
            // 解析为真实地址，使非法转发项通过校验。
            byte[] literal = parseLiteral(value.trim());
            return literal == null ? null : InetAddress.getByAddress(literal).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析 IPv4/IPv6 字面量为字节数组，不做任何 DNS 查询。
     *
     * @param value 地址文本
     * @return 地址字节；非字面量时返回 {@code null}
     */
    private byte[] parseLiteral(String value) {
        boolean ipv4 = value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean ipv6 = value.contains(":") && value.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (Exception error) {
            return null;
        }
    }

    /**
     * 判断指定地址是否属于任一可信代理网段。
     *
     * @param ip 待检查的规范化 IPv4/IPv6 地址
     * @return 命中可信代理 CIDR 配置时返回 {@code true}
     */
    private boolean isTrustedProxy(String ip) {
        return properties.getTrustedProxyCidrs().stream().anyMatch(cidr -> contains(cidr, ip));
    }

    /**
     * 判断 IP 地址是否包含在指定 CIDR 网段内。
     *
     * <p>未显式提供前缀长度时按单地址网段处理（IPv4 为 {@code /32}，IPv6 为 {@code /128}）。
     * 地址族不一致、前缀越界或格式非法时返回 {@code false}。</p>
     *
     * @param cidr CIDR 网段，例如 {@code 10.0.0.0/8}
     * @param ip 待检查的 IPv4/IPv6 地址
     * @return IP 位于指定网段内时返回 {@code true}
     */
    private boolean contains(String cidr, String ip) {
        try {
            String[] parts = cidr.trim().split("/", 2);
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] address = InetAddress.getByName(ip).getAddress();
            if (network.length != address.length) {
                return false;
            }
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * BITS_PER_BYTE;
            if (prefix < 0 || prefix > network.length * BITS_PER_BYTE) {
                return false;
            }
            for (int bit = 0; bit < prefix; bit++) {
                int mask = 1 << (BITS_PER_BYTE - 1 - bit % BITS_PER_BYTE);
                if ((network[bit / BITS_PER_BYTE] & mask) != (address[bit / BITS_PER_BYTE] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
