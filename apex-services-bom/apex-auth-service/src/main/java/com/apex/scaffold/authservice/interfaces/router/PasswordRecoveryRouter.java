package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.scaffold.authservice.interfaces.handler.PasswordRecoveryHandler;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * 注册密码找回路由。
     *
     * @return 三个端点的路由函数
     */
    @Bean
    @PasswordRecoveryOperations
    public RouterFunction<ServerResponse> passwordRecoveryRoutes() {
        return route().POST("/auth/v2/password-recovery/requests", passwordRecoveryHandler::initiate)
                .POST("/auth/v2/password-recovery/verifications", passwordRecoveryHandler::verify)
                .POST("/auth/v2/password-recovery/confirmations", passwordRecoveryHandler::confirm)
                .build();
    }
}
