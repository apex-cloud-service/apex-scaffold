package com.apex.scaffold.authcenter.config;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.scaffold.authcenter.interfaces.handler.IpAccessControlHandler;
import com.apex.scaffold.authcenter.interfaces.handler.MfaManagementHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 认证中心路由配置
 *
 * 使用WebFlux函数式路由注册认证中心的端点路由
 *
 * 端点列表：
 * - /api/mfa/** - 多因素认证管理
 * - /api/ip-access-control/** - IP访问控制管理
 *
 * @author Apex Team
 * @since 2026-09-25
 */
@Configuration
@RequiredArgsConstructor
public class AuthCenterRouterConfig {

    private final MfaManagementHandler mfaHandler;
    private final IpAccessControlHandler ipAccessControlHandler;

    /**
     * MFA多因素认证管理路由（/api/mfa/**，与 {@link com.apex.scaffold.authcenter.interfaces.router.MfaRouter}
     * 的 /api/v1/mfa/** 路由集相互独立，bean 命名区分避免冲突）
     */
    @Bean
    public RouterFunction<ServerResponse> authCenterMfaRoutes() {
        return route().POST("/api/mfa/totp/enable", mfaHandler::enableTotp)
                .POST("/api/mfa/totp/activate", mfaHandler::activateTotp)
                .POST("/api/mfa/sms/enable", mfaHandler::enableSms)
                .POST("/api/mfa/email/enable", mfaHandler::enableEmail)
                .DELETE("/api/mfa/{type}", mfaHandler::disableMfa)
                .POST("/api/mfa/verify", mfaHandler::verifyMfaCode)
                .POST("/api/mfa/send-code", mfaHandler::sendMfaCode)
                .GET("/api/mfa/configs", mfaHandler::getUserMfaConfigs)
                .GET("/api/mfa/status", mfaHandler::checkMfaStatus)
                .POST("/api/mfa/backup-codes/regenerate", mfaHandler::regenerateBackupCodes)
                .POST("/api/mfa/backup-codes/verify", mfaHandler::verifyBackupCode)
                .POST("/api/mfa/unlock", mfaHandler::unlockMfa)
                .build();
    }

    /**
     * IP访问控制路由
     */
    @Bean
    public RouterFunction<ServerResponse> ipAccessControlRoutes() {
        return route().POST("/api/ip-access-control/whitelist", ipAccessControlHandler::addToWhitelist)
                .POST("/api/ip-access-control/blacklist", ipAccessControlHandler::addToBlacklist)
                .DELETE("/api/ip-access-control/whitelist", ipAccessControlHandler::removeFromWhitelist)
                .DELETE("/api/ip-access-control/blacklist", ipAccessControlHandler::removeFromBlacklist)
                .GET("/api/ip-access-control/whitelist", ipAccessControlHandler::getWhitelist)
                .GET("/api/ip-access-control/blacklist", ipAccessControlHandler::getBlacklist)
                .PUT("/api/ip-access-control/{ruleId}/enable", ipAccessControlHandler::enableRule)
                .PUT("/api/ip-access-control/{ruleId}/disable", ipAccessControlHandler::disableRule)
                .DELETE("/api/ip-access-control/{ruleId}", ipAccessControlHandler::deleteRule)
                .POST("/api/ip-access-control/cleanup", ipAccessControlHandler::cleanupExpiredRules)
                .POST("/api/ip-access-control/check", ipAccessControlHandler::checkAccess)
                .build();
    }
}
