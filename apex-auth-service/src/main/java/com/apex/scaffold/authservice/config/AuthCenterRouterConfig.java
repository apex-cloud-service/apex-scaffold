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
 * 认证中心的无版本前缀路由配置，使用 WebFlux 函数式路由注册两类端点：
 *
 * <ul>
 *   <li>{@code /api/mfa/**}：当前用户的多因素认证管理；
 *   <li>{@code /api/ip-access-control/**}：IP 黑白名单规则管理。
 * </ul>
 *
 * <p>这两组路径不带版本号，与 {@link com.apex.scaffold.authservice.interfaces.router.MfaRouter}
 * 声明的 {@code /api/v1/mfa/**} 路由集并存且互不重叠，各自登记为独立 Bean；
 * 网关的 auth 路由需同时覆盖两套前缀，缺少任一前缀时对应接口不可达。
 * Handler 通过构造器注入，故 Bean 方法不接收参数。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class AuthCenterRouterConfig {

    private final MfaManagementHandler mfaHandler;
    private final IpAccessControlHandler ipAccessControlHandler;

    /**
     * MFA 多因素认证管理路由（{@code /api/mfa/**}，与 {@link com.apex.scaffold.authservice.interfaces.router.MfaRouter}
     * 的 {@code /api/v1/mfa/**} 路由集相互独立，Bean 命名区分避免冲突）。
     *
     * <p>覆盖 TOTP/SMS/邮件方式的启用与激活、按类型关闭、验证码校验与发送、
     * 配置与状态查询、备用码重新生成与校验，以及账号解锁；所有操作仅作用于当前用户。
     *
     * @return MFA 管理路由函数，挂载于 {@code /api/mfa}
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
     * IP 访问控制路由（{@code /api/ip-access-control/**}）。
     *
     * <p>提供黑白名单的增删查、规则启用/停用、按标识删除、过期规则清理与访问判定；
     * {@code DELETE /{ruleId}} 与 {@code POST /check} 等路径由具体 Handler 校验权限与租户范围。
     *
     * @return IP 规则管理路由函数，挂载于 {@code /api/ip-access-control}
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
