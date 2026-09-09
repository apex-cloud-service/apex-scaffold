package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 社交登录处理器
 *
 * <p>处理 OIDC 和第三方社交登录（GitHub/Google/微信）的认证流程。
 * 仅在 {@code keycloak.enabled=true} 时生效。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>{@link #initiateOidcLogin} - 发起 Keycloak OIDC 登录</li>
 *   <li>{@link #initiateSocialLogin} - 发起第三方社交登录</li>
 *   <li>{@link #handleOidcCallback} - 处理 OIDC 回调，返回 Token</li>
 * </ul>
 *
 * <h2>认证流程</h2>
 * <pre>
 * 1. 前端调用 /api/v1/auth/oidc/login 或 /api/v1/auth/social/{provider}/login
 * 2. 后端重定向到 Keycloak 或第三方 OAuth2 Provider
 * 3. 用户在 Keycloak 完成认证
 * 4. Keycloak 回调到 Spring Security OAuth2 Client
 * 5. OidcAuthenticationSuccessHandler 生成 JWT Token
 * 6. 重定向到前端 callback URL，携带 accessToken 和 refreshToken
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 * @see com.apex.scaffold.interfaces.security.OidcAuthenticationSuccessHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class SocialLoginHandler {

    /**
     * 发起 OIDC 登录
     *
     * <p>重定向到 Spring Security OAuth2 Client 的 {@code /oauth2/authorization/keycloak} 端点，
     * 触发 OIDC 授权码流程。
     *
     * @param request HTTP 请求
     * @return 302 重定向响应，跳转到 Keycloak 登录页
     */
    public Mono<ServerResponse> initiateOidcLogin(ServerRequest request) {
        log.info("Initiating OIDC login");

        String redirectUrl = "/oauth2/authorization/keycloak";

        return ServerResponse.temporaryRedirect(URI.create(redirectUrl)).build();
    }

    /**
     * 发起社交登录
     *
     * <p>根据 provider 参数重定向到对应的 OAuth2 Provider：
     * <ul>
     *   <li>github - GitHub OAuth2</li>
     *   <li>google - Google OAuth2</li>
     *   <li>wechat - 微信开放平台</li>
     * </ul>
     *
     * @param request HTTP 请求，路径参数 {@code provider} 指定登录方式
     * @return 302 重定向响应 或 400 错误（不支持的 provider）
     */
    public Mono<ServerResponse> initiateSocialLogin(ServerRequest request) {
        String provider = request.pathVariable("provider");
        log.info("Initiating social login: provider={}", provider);

        String redirectUrl =
                switch (provider.toLowerCase()) {
                    case "github" -> "/oauth2/authorization/github";
                    case "google" -> "/oauth2/authorization/google";
                    case "wechat" -> "/oauth2/authorization/wechat";
                    default -> {
                        log.warn("Unsupported social login provider: {}", provider);
                        yield null;
                    }
                };

        if (redirectUrl == null) {
            return ServerResponse.badRequest()
                    .bodyValue(Result.error(400, "Unsupported social login provider: " + provider));
        }

        return ServerResponse.temporaryRedirect(URI.create(redirectUrl)).build();
    }

    /**
     * 处理 OIDC 回调
     *
     * <p>前端从重定向 URL 中提取 Token 后，可调用此接口验证并获取 Token 信息。
     * 实际的 OIDC 回调由 Spring Security OAuth2 Client 和
     * {@link com.apex.scaffold.interfaces.security.OidcAuthenticationSuccessHandler} 处理。
     *
     * @param request HTTP 请求，Query 参数包含 accessToken、refreshToken、userId
     * @return 200 JSON 响应，包含 Token 信息 或 400 错误（缺少参数）
     */
    public Mono<ServerResponse> handleOidcCallback(ServerRequest request) {
        log.info("Handling OIDC callback");

        String accessToken = request.queryParam("accessToken").orElse(null);
        String refreshToken = request.queryParam("refreshToken").orElse(null);
        String userId = request.queryParam("userId").orElse(null);

        if (accessToken == null || refreshToken == null || userId == null) {
            log.error("Missing required callback parameters");
            return ServerResponse.badRequest().bodyValue(Result.error(400, "Missing required callback parameters"));
        }

        return ServerResponse.ok()
                .bodyValue(Result.success(java.util.Map.of(
                        "accessToken", accessToken,
                        "refreshToken", refreshToken,
                        "userId", userId)));
    }
}
