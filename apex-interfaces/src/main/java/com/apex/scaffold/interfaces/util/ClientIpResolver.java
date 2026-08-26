package com.apex.scaffold.interfaces.util;

import com.apex.scaffold.interfaces.filter.IpRateLimitProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 安全解析器。
 *
 * <p>该组件根据 TCP 直连地址、{@code X-Forwarded-For} 请求头和
 * {@code X-Real-IP} 请求头解析真实客户端地址。为防止客户端伪造转发头，
 * 只有当 TCP 直连来源命中 {@link IpRateLimitProperties} 配置的可信代理 CIDR
 * 配置的可信代理网段时，才会采信代理传递的地址信息。</p>
 *
 * <p>{@code X-Forwarded-For} 按照代理链从右向左检查，跳过所有可信代理，
 * 第一个非可信地址被视为原始客户端地址。如果代理链中没有可用地址，
 * 则依次回退到 {@code X-Real-IP} 和 TCP 直连地址。无法解析的输入不会向外
 * 抛出异常，而是按无效地址处理。</p>
 *
 * <p>该解析结果可用于接口限流、安全审计和风险控制，但可信代理网段必须
 * 与实际负载均衡器、反向代理的网络边界保持一致。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see IpRateLimitProperties
 * @see InetSocketAddress
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final int BITS_PER_BYTE = Byte.SIZE;

    /** 可信代理及相关 IP 限流配置。 */
    private final IpRateLimitProperties properties;

    /**
     * 解析请求对应的真实客户端 IP 地址。
     *
     * <p>当直连地址不属于可信代理时，本方法忽略全部转发头并直接返回直连
     * 地址；当直连地址属于可信代理时，优先从 {@code X-Forwarded-For}
     * 代理链中寻找最靠右的非可信地址，再回退到 {@code X-Real-IP}。</p>
     *
     * @param remoteAddress TCP 连接的远端地址，允许为 {@code null}
     * @param forwardedFor {@code X-Forwarded-For} 的全部请求头值，允许为
     *                     {@code null} 或空集合
     * @param realIp {@code X-Real-IP} 请求头值，允许为 {@code null} 或空白字符串
     * @return 规范化后的客户端 IP；直连地址不可用且无法安全解析时返回 {@code null}
     */
    public String resolve(InetSocketAddress remoteAddress, List<String> forwardedFor, String realIp) {
        String remoteIp = addressOf(remoteAddress);
        if (remoteIp == null || !isTrustedProxy(remoteIp)) {
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
     * <p>方法会移除首尾空白，并通过 {@link InetAddress#getByName(String)}
     * 转换为规范化的数字地址。非法地址和解析失败统一返回 {@code null}，
     * 避免不可信请求头中断请求处理链。</p>
     *
     * @param value 待解析的地址文本，允许为 {@code null} 或空白字符串
     * @return 规范化后的 IPv4/IPv6 地址；解析失败时返回 {@code null}
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 判断指定地址是否属于任一可信代理网段。
     *
     * @param ip 待检查的规范化 IPv4/IPv6 地址
     * @return 命中可信代理 CIDR 配置时返回 {@code true}，否则返回 {@code false}
     */
    private boolean isTrustedProxy(String ip) {
        return properties.getTrustedProxyCidrs().stream().anyMatch(cidr -> contains(cidr, ip));
    }

    /**
     * 判断 IP 地址是否包含在指定 CIDR 网段内。
     *
     * <p>同时支持 IPv4 和 IPv6。未显式提供前缀长度时，按照单地址网段处理，
     * 即 IPv4 使用 {@code /32}，IPv6 使用 {@code /128}。地址族不一致、前缀
     * 越界、CIDR 格式非法或地址解析失败时均返回 {@code false}。</p>
     *
     * @param cidr CIDR 网段，例如 {@code 10.0.0.0/8} 或 {@code 2001:db8::/32}
     * @param ip 待检查的 IPv4/IPv6 地址
     * @return IP 位于指定网段内时返回 {@code true}，否则返回 {@code false}
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
