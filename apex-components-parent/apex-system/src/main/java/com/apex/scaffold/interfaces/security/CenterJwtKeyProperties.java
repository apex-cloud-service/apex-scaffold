package com.apex.scaffold.interfaces.security;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证中心令牌验签公钥配置（{@code apex.system.jwt-center.*}）。
 *
 * <p>统一认证收口（A1）：主应用需验签认证中心 RS256 令牌。公钥优先经共享
 * {@code apex.jwks.remote-url} 从认证中心 JWKS 端点自动拉取；静态配置仅作
 * 拉取不可用时的兜底（公钥可安全下发，不含私钥）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.system.jwt-center")
public class CenterJwtKeyProperties {

    /**
     * 认证中心验签公钥静态清单（kid → X509 DER Base64）。
     *
     * <p>与认证中心 JWKS 端点暴露的 {@code kid}（形如 {@code key-<uuid>}）一致；
     * 同 kid 与拉取快照并存时以拉取快照为准。
     */
    private Map<String, String> publicKeys = new LinkedHashMap<>();

    /**
     * 是否允许 HS 系列算法过渡验签。
     *
     * <p>主应用自有令牌（C 端登录链路）仍为 HS256 签发，迁入统一密钥体系前
     * 本开关不得关闭；关闭后仅接受认证中心 RS256 令牌。
     */
    private boolean legacyHmacEnabled = true;
}
