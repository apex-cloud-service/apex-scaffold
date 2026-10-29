package com.apex.scaffold.application.support.sso;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SSO 协议适配器工厂类
 *
 * <p>根据协议类型创建对应的 SSO 协议适配器实例。支持多种 SSO 协议的动态适配,
 * 使用工厂模式实现协议适配器的统一管理和创建。
 *
 * <h2>支持的协议类型</h2>
 * <ul>
 *   <li>SAML - SAML 2.0 标准协议</li>
 *   <li>CAS - Central Authentication Service 协议</li>
 *   <li>LDAP - 轻量级目录访问协议</li>
 *   <li>OIDC - OpenID Connect 协议</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>
 * SsoProtocolAdapter adapter = factory.getAdapter("SAML");
 * Mono&lt;Map&lt;String, Object&gt;&gt; result = adapter.authenticate(config, params);
 * </pre>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoProtocolAdapterFactory {

    private final SamlProtocolAdapter samlProtocolAdapter;
    private final CasProtocolAdapter casProtocolAdapter;
    private final LdapProtocolAdapter ldapProtocolAdapter;
    private final OidcProtocolAdapter oidcProtocolAdapter;

    private final Map<String, SsoProtocolAdapter> adapterCache = new ConcurrentHashMap<>();

    /**
     * 根据协议类型获取对应的协议适配器
     *
     * <p>支持的协议类型(不区分大小写):
     * <ul>
     *   <li>SAML / saml</li>
     *   <li>CAS / cas</li>
     *   <li>LDAP / ldap</li>
     *   <li>OIDC / oidc</li>
     * </ul>
     *
     * @param protocolType 协议类型
     * @return 协议适配器实例
     * @throws IllegalArgumentException 如果协议类型不支持
     */
    public SsoProtocolAdapter getAdapter(String protocolType) {
        if (protocolType == null || protocolType.trim().isEmpty()) {
            throw new IllegalArgumentException("协议类型不能为空");
        }

        String normalizedType = protocolType.trim().toUpperCase();

        return adapterCache.computeIfAbsent(normalizedType, type -> {
            log.debug("创建 SSO 协议适配器: {}", type);
            return switch (type) {
                case "SAML" -> samlProtocolAdapter;
                case "CAS" -> casProtocolAdapter;
                case "LDAP" -> ldapProtocolAdapter;
                case "OIDC" -> oidcProtocolAdapter;
                default -> {
                    log.error("不支持的 SSO 协议类型: {}", type);
                    throw new IllegalArgumentException("不支持的 SSO 协议类型: " + type);
                }
            };
        });
    }

    /**
     * 判断是否支持指定的协议类型
     *
     * @param protocolType 协议类型
     * @return 如果支持返回 true,否则返回 false
     */
    public boolean isSupported(String protocolType) {
        if (protocolType == null || protocolType.trim().isEmpty()) {
            return false;
        }

        String normalizedType = protocolType.trim().toUpperCase();
        return switch (normalizedType) {
            case "SAML", "CAS", "LDAP", "OIDC" -> true;
            default -> false;
        };
    }
}
