package com.apex.scaffold.authservice.config;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.scaffold.authservice.interfaces.handler.IpAccessControlHandler;
import com.apex.scaffold.authservice.interfaces.handler.MfaManagementHandler;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
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
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class AuthCenterRouterConfig {

    private final MfaManagementHandler mfaHandler;
    private final IpAccessControlHandler ipAccessControlHandler;

    /**
     * MFA多因素认证管理路由（/api/mfa/**，与 {@link com.apex.scaffold.authservice.interfaces.router.MfaRouter}
     * 的 /api/v1/mfa/** 路由集相互独立，bean 命名区分避免冲突）
     *
     * @return MFA 管理路由
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/mfa/totp/enable",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "enableTotp"),
        @RouterOperation(
                path = "/api/mfa/totp/activate",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "activateTotp"),
        @RouterOperation(
                path = "/api/mfa/sms/enable",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "enableSms"),
        @RouterOperation(
                path = "/api/mfa/email/enable",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "enableEmail"),
        @RouterOperation(
                path = "/api/mfa/{type}",
                method = RequestMethod.DELETE,
                beanClass = MfaManagementHandler.class,
                beanMethod = "disableMfa"),
        @RouterOperation(
                path = "/api/mfa/verify",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "verifyMfaCode"),
        @RouterOperation(
                path = "/api/mfa/send-code",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "sendMfaCode"),
        @RouterOperation(
                path = "/api/mfa/configs",
                method = RequestMethod.GET,
                beanClass = MfaManagementHandler.class,
                beanMethod = "getUserMfaConfigs"),
        @RouterOperation(
                path = "/api/mfa/status",
                method = RequestMethod.GET,
                beanClass = MfaManagementHandler.class,
                beanMethod = "checkMfaStatus"),
        @RouterOperation(
                path = "/api/mfa/backup-codes/regenerate",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "regenerateBackupCodes"),
        @RouterOperation(
                path = "/api/mfa/backup-codes/verify",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "verifyBackupCode"),
        @RouterOperation(
                path = "/api/mfa/unlock",
                method = RequestMethod.POST,
                beanClass = MfaManagementHandler.class,
                beanMethod = "unlockMfa")
    })
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
     * IP访问控制路由。
     *
     * @return IP 规则管理路由
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/ip-access-control/whitelist",
                method = RequestMethod.POST,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "addToWhitelist"),
        @RouterOperation(
                path = "/api/ip-access-control/blacklist",
                method = RequestMethod.POST,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "addToBlacklist"),
        @RouterOperation(
                path = "/api/ip-access-control/whitelist",
                method = RequestMethod.DELETE,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "removeFromWhitelist"),
        @RouterOperation(
                path = "/api/ip-access-control/blacklist",
                method = RequestMethod.DELETE,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "removeFromBlacklist"),
        @RouterOperation(
                path = "/api/ip-access-control/whitelist",
                method = RequestMethod.GET,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "getWhitelist"),
        @RouterOperation(
                path = "/api/ip-access-control/blacklist",
                method = RequestMethod.GET,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "getBlacklist"),
        @RouterOperation(
                path = "/api/ip-access-control/{ruleId}/enable",
                method = RequestMethod.PUT,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "enableRule"),
        @RouterOperation(
                path = "/api/ip-access-control/{ruleId}/disable",
                method = RequestMethod.PUT,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "disableRule"),
        @RouterOperation(
                path = "/api/ip-access-control/{ruleId}",
                method = RequestMethod.DELETE,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "deleteRule"),
        @RouterOperation(
                path = "/api/ip-access-control/cleanup",
                method = RequestMethod.POST,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "cleanupExpiredRules"),
        @RouterOperation(
                path = "/api/ip-access-control/check",
                method = RequestMethod.POST,
                beanClass = IpAccessControlHandler.class,
                beanMethod = "checkAccess")
    })
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
