package com.apex.scaffold.application.support.sso;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SSO 协议适配器工厂类
 *
 * <p>根据协议类型返回对应的 SSO 协议适配器实例。四个内置协议在构造时一次性登记为
 * 不可变注册表，{@link #getAdapter(String)} 与 {@link #isSupported(String)} 共用同一来源，
 * 避免两处协议清单漂移。适配器本身由 Spring 容器管理，本工厂不缓存也不创建新实例。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>SAML - SAML 2.0 标准协议</li>
 *   <li>CAS - Central Authentication Service 协议</li>
 *   <li>LDAP - 轻量级目录访问协议</li>
 *   <li>OIDC - OpenID Connect 协议</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * SsoProtocolAdapter adapter = factory.getAdapter("SAML");
 * Mono&lt;Map&lt;String, Object&gt;&gt; result = adapter.authenticate(config, params);
 * </pre>
 *
 * <p>说明：注册表只登记内置协议，不提供 SPI 或运行时新增协议的扩展点；
 * 新增内置协议需在构造器登记后同步本类注释。
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Slf4j
@Component
public class SsoProtocolAdapterFactory {

    /** 协议类型 → 已装配适配器；构造后不再变化 */
    private final Map<String, SsoProtocolAdapter> adapters;

    /**
     * 构造 SSO 协议适配器工厂。
     *
     * @param samlProtocolAdapter SAML 2.0 适配器
     * @param casProtocolAdapter CAS 适配器
     * @param ldapProtocolAdapter LDAP 适配器
     * @param oidcProtocolAdapter OIDC 适配器
     */
    public SsoProtocolAdapterFactory(
            SamlProtocolAdapter samlProtocolAdapter,
            CasProtocolAdapter casProtocolAdapter,
            LdapProtocolAdapter ldapProtocolAdapter,
            OidcProtocolAdapter oidcProtocolAdapter) {
        Map<String, SsoProtocolAdapter> registry = new LinkedHashMap<>(8);
        registry.put("SAML", samlProtocolAdapter);
        registry.put("CAS", casProtocolAdapter);
        registry.put("LDAP", ldapProtocolAdapter);
        registry.put("OIDC", oidcProtocolAdapter);
        this.adapters = Map.copyOf(registry);
    }

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
        String normalized = protocolType.trim().toUpperCase(Locale.ROOT);
        SsoProtocolAdapter adapter = adapters.get(normalized);
        if (adapter == null) {
            log.error("不支持的 SSO 协议类型: {}", normalized);
            throw new IllegalArgumentException("不支持的 SSO 协议类型: " + normalized);
        }
        return adapter;
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
        return adapters.containsKey(protocolType.trim().toUpperCase(Locale.ROOT));
    }
}
