package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PasswordRecoveryConfirmResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PasswordRecoveryInitiateResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PasswordRecoveryVerifyResult;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyRequest;
import com.apex.scaffold.authservice.interfaces.handler.PasswordRecoveryHandler;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 自助密码找回路由。
 *
 * <p>注册匿名恢复链路的三个 POST 端点；路径已在
 * {@code AuthCenterConfig} 的精确匿名白名单中放行。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        description = "JWT Bearer Token")
public class PasswordRecoveryRouter {

    private final PasswordRecoveryHandler passwordRecoveryHandler;

    /**
     * 密码找回路由定义。
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/auth/v2/password-recovery/requests",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                produces = MediaType.APPLICATION_JSON_VALUE,
                beanClass = PasswordRecoveryHandler.class,
                beanMethod = "initiate",
                operation =
                        @io.swagger.v3.oas.annotations.Operation(
                                operationId = "initiatePasswordRecovery",
                                summary = "发起密码找回申请",
                                description = "按已验证邮箱发起密码找回；对账号存在与否保持同一响应形态",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PasswordRecoveryInitiateRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "申请受理",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    PasswordRecoveryInitiateResult
                                                                                            .class)))
                                })),
        @RouterOperation(
                path = "/auth/v2/password-recovery/verifications",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                produces = MediaType.APPLICATION_JSON_VALUE,
                beanClass = PasswordRecoveryHandler.class,
                beanMethod = "verify",
                operation =
                        @io.swagger.v3.oas.annotations.Operation(
                                operationId = "verifyPasswordRecovery",
                                summary = "校验找回验证码",
                                description = "校验一次性验证码并颁发一次性确认令牌；失败计入次数，达到上限后恢复单作废",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PasswordRecoveryVerifyRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "校验通过",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    PasswordRecoveryVerifyResult
                                                                                            .class)))
                                })),
        @RouterOperation(
                path = "/auth/v2/password-recovery/confirmations",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                produces = MediaType.APPLICATION_JSON_VALUE,
                beanClass = PasswordRecoveryHandler.class,
                beanMethod = "confirm",
                operation =
                        @io.swagger.v3.oas.annotations.Operation(
                                operationId = "confirmPasswordRecovery",
                                summary = "确认重设密码",
                                description = "原子消费确认令牌，执行密码策略并重设密码，撤销全部会话；撤销失败返回 PENDING",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PasswordRecoveryConfirmRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "密码已重设",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    PasswordRecoveryConfirmResult
                                                                                            .class)))
                                }))
    })
    public RouterFunction<ServerResponse> passwordRecoveryRoutes() {
        return route().POST("/auth/v2/password-recovery/requests", passwordRecoveryHandler::initiate)
                .POST("/auth/v2/password-recovery/verifications", passwordRecoveryHandler::verify)
                .POST("/auth/v2/password-recovery/confirmations", passwordRecoveryHandler::confirm)
                .build();
    }
}
