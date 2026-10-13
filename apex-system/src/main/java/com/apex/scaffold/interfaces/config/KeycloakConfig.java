package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak 集成配置类
 *
 * <p>负责加载 Keycloak 相关配置参数并初始化 Keycloak Admin Client。
 * 仅在 {@code keycloak.enabled=true} 时生效，实现集成开关可控。
 *
 * <h2>配置绑定</h2>
 * <p>通过 {@link ConfigurationProperties} 从配置文件读取 {@code keycloak.*} 前缀的配置项：
 * <ul>
 *   <li>{@code keycloak.server-url} - Keycloak 服务器地址</li>
 *   <li>{@code keycloak.realm} - Realm 名称</li>
 *   <li>{@code keycloak.client-id} - OAuth2 Client ID</li>
 *   <li>{@code keycloak.client-secret} - OAuth2 Client Secret（可选）</li>
 *   <li>{@code keycloak.admin-username} - 管理员用户名</li>
 *   <li>{@code keycloak.admin-password} - 管理员密码</li>
 *   <li>{@code keycloak.user-sync-enabled} - 用户同步开关</li>
 *   <li>{@code keycloak.frontend-callback-url} - 前端回调地址</li>
 * </ul>
 *
 * <h2>Bean 注册</h2>
 * <ul>
 *   <li>{@link #keycloakAdminClient()} - Keycloak Admin REST API 客户端</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * # application-dev.yml
 * keycloak:
 *   enabled: true
 *   server-url: http://localhost:8180
 *   realm: apex
 *   client-id: apex-client
 *   admin-username: admin
 *   admin-password: admin123
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 * @see Keycloak
 * @see KeycloakBuilder
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "keycloak")
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Validated
@Data
public class KeycloakConfig {

    /**
     * Keycloak 服务器地址
     *
     * <p>示例：{@code http://localhost:8180} 或 {@code https://keycloak.example.com}
     */
    @NotBlank(message = "Keycloak server-url cannot be blank")
    private String serverUrl;

    /**
     * Keycloak Realm 名称
     *
     * <p>Realm 是 Keycloak 中的租户隔离单位，不同 Realm 之间的用户、客户端、角色完全隔离。
     */
    @NotBlank(message = "Keycloak realm cannot be blank")
    private String realm;

    /**
     * Keycloak 管理员用户名
     *
     * <p>用于初始化 Admin Client，调用 Keycloak Admin REST API 管理用户、角色等资源。
     */
    @NotBlank(message = "Keycloak admin-username cannot be blank")
    private String adminUsername;

    /**
     * Keycloak 管理员密码
     *
     * <p>生产环境建议通过环境变量 {@code KEYCLOAK_ADMIN_PASSWORD} 注入，避免明文存储。
     */
    @NotBlank(message = "Keycloak admin-password cannot be blank")
    private String adminPassword;

    /**
     * Keycloak Admin Client ID
     *
     * <p>默认使用 {@code admin-cli}，这是 Keycloak 内置的管理客户端。
     */
    @NotBlank(message = "Keycloak admin-client-id cannot be blank")
    private String adminClientId;

    /**
     * OAuth2 Client ID
     *
     * <p>前端 OIDC 登录使用的 Client ID，需要在 Keycloak Realm 中预先创建。
     */
    @NotBlank(message = "Keycloak client-id cannot be blank")
    private String clientId;

    /**
     * OAuth2 Client Secret
     *
     * <p>Confidential Client 模式下必填。Public Client 模式（如 SPA 前端）可为空。
     */
    private String clientSecret;

    /**
     * 用户同步开关
     *
     * <p>启用后，OIDC 登录时如果本地数据库没有该用户，会自动从 Keycloak 同步创建。
     * 关闭后仅允许已存在的用户登录。
     */
    private boolean userSyncEnabled = true;

    /**
     * 前端 OIDC 回调地址
     *
     * <p>OIDC 认证成功后，后端将生成的 JWT Token 作为 URL 参数重定向到该地址。
     * 示例：{@code http://localhost:3000/auth/callback}
     */
    @NotBlank(message = "Keycloak frontend-callback-url cannot be blank")
    private String frontendCallbackUrl;

    /**
     * 初始化 Keycloak Admin Client
     *
     * <p>使用 Resource Owner Password Credentials Grant（密码模式）认证，
     * 获取 Admin 权限，可调用 Keycloak Admin REST API 管理用户、角色等资源。
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>Admin Client 使用阻塞式 HTTP 调用，需通过
     *       {@link BlockingIoSchedulers#remote()} 隔离到专用线程池</li>
     *   <li>生产环境建议限制 Admin 权限范围，遵循最小权限原则</li>
     *   <li>Admin 凭证应通过环境变量注入，避免硬编码</li>
     * </ul>
     *
     * @return Keycloak Admin Client 实例
     */
    @Bean
    @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
    public Keycloak keycloakAdminClient() {
        log.info(
                "Initializing Keycloak Admin Client: server={}, realm={}, adminClientId={}",
                serverUrl,
                realm,
                adminClientId);

        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();

        log.info("Keycloak Admin Client initialized successfully");
        return keycloak;
    }
}
