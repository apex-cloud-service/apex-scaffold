package com.apex.scaffold.application.support.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SsoProtocolAdapterFactory} 协议分派测试。
 *
 * <p>覆盖四个内置协议、大小写与空白输入、未知协议、非英语默认 Locale，
 * 以及返回的实例与容器装配的同一 Bean（不重复创建）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@DisplayName("SSO 协议适配器工厂分派")
class SsoProtocolAdapterFactoryTest {

    /** 适配器协作者由容器注入，本测试只验证分派，因此以 mock 代替真实依赖。 */
    private final SamlProtocolAdapter saml = mock(SamlProtocolAdapter.class);

    private final CasProtocolAdapter cas = mock(CasProtocolAdapter.class);
    private final LdapProtocolAdapter ldap = mock(LdapProtocolAdapter.class);
    private final OidcProtocolAdapter oidc = mock(OidcProtocolAdapter.class);
    private final SsoProtocolAdapterFactory factory = new SsoProtocolAdapterFactory(saml, cas, ldap, oidc);

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    @DisplayName("四个内置协议均返回对应已装配实例")
    void resolvesAllBuiltInProtocols() {
        assertThat(factory.getAdapter("SAML")).isSameAs(saml);
        assertThat(factory.getAdapter("CAS")).isSameAs(cas);
        assertThat(factory.getAdapter("LDAP")).isSameAs(ldap);
        assertThat(factory.getAdapter("OIDC")).isSameAs(oidc);
    }

    @Test
    @DisplayName("协议名按 Locale.ROOT 规范化：大小写无关，不依赖默认 Locale")
    void normalizesCaseWithRootLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(factory.getAdapter("saml")).isSameAs(saml);
        assertThat(factory.getAdapter("  Oidc ")).isSameAs(oidc);
        assertThat(factory.isSupported("cas")).isTrue();
        assertThat(factory.isSupported(" ldap ")).isTrue();
    }

    @Test
    @DisplayName("空值与空白协议不视为支持；getAdapter 保留原「协议类型不能为空」消息")
    void rejectsBlankProtocol() {
        assertThat(factory.isSupported(null)).isFalse();
        assertThat(factory.isSupported("")).isFalse();
        assertThat(factory.isSupported("   ")).isFalse();
        assertThatThrownBy(() -> factory.getAdapter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("协议类型不能为空");
        assertThatThrownBy(() -> factory.getAdapter("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("协议类型不能为空");
    }

    @Test
    @DisplayName("未知协议抛出 IllegalArgumentException，且异常类型与消息保持原契约")
    void rejectsUnknownProtocol() {
        assertThatThrownBy(() -> factory.getAdapter("OAUTH2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 SSO 协议类型")
                .hasMessageContaining("OAUTH2");
    }

    @Test
    @DisplayName("重复查表返回同一实例，不产生新对象")
    void reusesRegisteredInstance() {
        assertThat(factory.getAdapter("SAML")).isSameAs(factory.getAdapter("SAML"));
        assertThat(factory.getAdapter("oidc")).isSameAs(factory.getAdapter("OIDC"));
    }
}
