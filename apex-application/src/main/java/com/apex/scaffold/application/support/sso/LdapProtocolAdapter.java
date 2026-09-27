package com.apex.scaffold.application.support.sso;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * LDAP 协议适配器。
 *
 * <p>实现基于 LDAP(Lightweight Directory Access Protocol)的身份认证,
 * 支持标准 LDAP 绑定认证和用户属性查询。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class LdapProtocolAdapter implements SsoProtocolAdapter {

    /** Map 初始容量:用于响应结果(预期 2-5 个键值对)。 */
    private static final int RESULT_MAP_CAPACITY = 8;

    /** Map 初始容量:用于用户属性(预期 2-10 个属性)。 */
    private static final int ATTRIBUTES_MAP_CAPACITY = 16;

    @Override
    public Mono<Map<String, Object>> initiate(Map<String, Object> config, String callbackUrl) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
            result.put("protocol", "LDAP");
            result.put("message", "LDAP 无重定向流程,直接提交用户名密码到回调地址");
            return result;
        });
    }

    @Override
    public Mono<Map<String, Object>> authenticate(Map<String, Object> config, Map<String, String> callbackParams) {
        String username = callbackParams.get("username");
        String password = callbackParams.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Mono.error(new IllegalArgumentException("LDAP 用户名或密码缺失"));
        }

        return Mono.fromCallable(() -> performLdapBind(config, username, password))
                .subscribeOn(BlockingIoSchedulers.remote())
                .doOnError(e -> log.error("LDAP 认证失败: username={}, error={}", username, e.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> logout(Map<String, Object> config, String sessionToken) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
            result.put("success", true);
            result.put("message", "LDAP 无状态协议,仅执行本地登出");
            return result;
        });
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(Map<String, Object> config) {
        return Mono.fromCallable(() -> {
            Map<String, Object> metadata = new HashMap<>(RESULT_MAP_CAPACITY);
            metadata.put("protocol", "LDAP");
            metadata.put("ldapUrl", config.get("ldapUrl"));
            metadata.put("baseDn", config.get("baseDn"));
            metadata.put("userSearchFilter", config.getOrDefault("userSearchFilter", "(uid={0})"));
            return metadata;
        });
    }

    @Override
    public Mono<Map<String, Object>> testConnection(Map<String, Object> config) {
        String adminDn = (String) config.get("adminDn");
        String adminPassword = (String) config.get("adminPassword");

        if (adminDn == null || adminPassword == null) {
            return Mono.fromCallable(() -> {
                Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
                result.put("success", false);
                result.put("message", "LDAP 管理员凭证配置缺失");
                return result;
            });
        }

        return Mono.fromCallable(() -> {
                    DirContext ctx = null;
                    try {
                        ctx = createDirContext(config, adminDn, adminPassword);
                        Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
                        result.put("success", true);
                        result.put("message", "LDAP 连接成功");
                        return result;
                    } catch (Exception e) {
                        Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
                        result.put("success", false);
                        result.put("message", "LDAP 连接失败: " + e.getMessage());
                        return result;
                    } finally {
                        closeDirContext(ctx);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.remote());
    }

    private Map<String, Object> performLdapBind(Map<String, Object> config, String username, String password)
            throws Exception {
        String ldapUrl = (String) config.get("ldapUrl");
        String baseDn = (String) config.get("baseDn");
        String userSearchFilter = (String) config.getOrDefault("userSearchFilter", "(uid={0})");
        String adminDn = (String) config.get("adminDn");
        String adminPassword = (String) config.get("adminPassword");

        DirContext adminCtx = null;
        DirContext userCtx = null;

        try {
            adminCtx = createDirContext(config, adminDn, adminPassword);

            String filter = userSearchFilter.replace("{0}", username);
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[] {"cn", "mail", "displayName", "uid"});

            NamingEnumeration<SearchResult> results = adminCtx.search(baseDn, filter, searchControls);

            if (!results.hasMore()) {
                throw new RuntimeException("LDAP 用户不存在: " + username);
            }

            SearchResult searchResult = results.next();
            String userDn = searchResult.getNameInNamespace();
            Attributes attributes = searchResult.getAttributes();

            userCtx = createDirContext(config, userDn, password);

            Map<String, Object> userInfo = new HashMap<>(RESULT_MAP_CAPACITY);
            userInfo.put("userId", getAttributeValue(attributes, "uid", username));
            userInfo.put("username", getAttributeValue(attributes, "cn", username));

            Map<String, String> userAttributes = new HashMap<>(ATTRIBUTES_MAP_CAPACITY);
            String email = getAttributeValue(attributes, "mail", null);
            if (email != null) {
                userAttributes.put("email", email);
            }
            String displayName = getAttributeValue(attributes, "displayName", null);
            if (displayName != null) {
                userAttributes.put("displayName", displayName);
            }
            userInfo.put("attributes", userAttributes);

            return userInfo;
        } finally {
            closeDirContext(adminCtx);
            closeDirContext(userCtx);
        }
    }

    private DirContext createDirContext(Map<String, Object> config, String principal, String credentials)
            throws Exception {
        String ldapUrl = (String) config.get("ldapUrl");
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, credentials);
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");
        return new InitialDirContext(env);
    }

    private void closeDirContext(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception e) {
                log.warn("关闭 LDAP 连接失败: {}", e.getMessage());
            }
        }
    }

    private String getAttributeValue(Attributes attributes, String attributeName, String defaultValue)
            throws Exception {
        Attribute attribute = attributes.get(attributeName);
        if (attribute != null && attribute.get() != null) {
            return attribute.get().toString();
        }
        return defaultValue;
    }
}
