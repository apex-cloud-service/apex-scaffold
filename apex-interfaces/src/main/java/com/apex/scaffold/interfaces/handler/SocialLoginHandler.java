package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 社交登录处理器（函数式端点）。
 *
 * <p>提供 Keycloak OIDC 登录和第三方社交登录（GitHub、Google、微信）的认证接口，
 * 处理 OAuth2 授权流程的发起、回调和令牌交换。仅在 {@code keycloak.enabled=true} 时生效。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /auth/oidc/login - 发起 Keycloak OIDC 登录，重定向到 Keycloak 登录页面</li>
 *   <li>GET /auth/social/{provider} - 发起第三方社交登录，支持 github、google、wechat</li>
 *   <li>GET /auth/oidc/callback - 处理 OIDC 回调，验证并返回 Token 信息</li>
 * </ul>
 *
 * <h2>支持的社交登录提供商</h2>
 * <ul>
 *   <li>github：GitHub OAuth2 登录</li>
 *   <li>google：Google OAuth2 登录</li>
 *   <li>wechat：微信 OAuth2 登录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Tag(name = "社交登录", description = "OIDC和第三方社交登录(GitHub/Google/微信)认证接口")
public class SocialLoginHandler {

    @Operation(
            operationId = "initiateOidcLogin",
            summary = "发起Keycloak OIDC登录",
            description = "重定向到Keycloak登录页面,触发OIDC授权码流程")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到Keycloak登录页面")})
    public Mono<ServerResponse> initiateOidcLogin(ServerRequest request) {
        log.info("Initiating OIDC login");

        String redirectUrl = "/oauth2/authorization/keycloak";

        return ServerResponse.temporaryRedirect(URI.create(redirectUrl)).build();
    }

    @Operation(
            operationId = "initiateSocialLogin",
            summary = "发起第三方社交登录",
            description = "根据provider参数重定向到对应的OAuth2提供商(github/google/wechat)")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "重定向到第三方OAuth2提供商登录页面"),
        @ApiResponse(
                responseCode = "400",
                description = "不支持的社交登录提供商",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @Parameter(name = "provider", description = "社交登录提供商:github、google或wechat", required = true, in = ParameterIn.PATH)
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

    @Operation(operationId = "handleOidcCallback", summary = "处理OIDC回调", description = "验证并返回OIDC认证后的Token信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "返回Token信息",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "缺少必需的回调参数",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
