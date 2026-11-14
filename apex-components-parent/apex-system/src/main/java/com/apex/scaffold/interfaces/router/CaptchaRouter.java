package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.CaptchaHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 验证码路由配置（/api/v1/auth/captcha）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class CaptchaRouter {

    /**
     * 图形验证码路由（/api/v1/auth/captcha）。
     *
     * <p>提供验证码获取与一次性校验端点，供登录场景防自动化爆破使用。</p>
     *
     * @param handler 图形验证码处理器
     * @return 验证码路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/captcha",
                method = RequestMethod.GET,
                beanClass = CaptchaHandler.class,
                beanMethod = "generate",
                operation =
                        @Operation(
                                summary = "获取图形验证码",
                                description = "生成图形验证码，用于登录防自动化爆破",
                                tags = {"验证码"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/captcha/verify",
                method = RequestMethod.POST,
                beanClass = CaptchaHandler.class,
                beanMethod = "verify",
                operation =
                        @Operation(
                                summary = "校验图形验证码",
                                description = "一次性校验图形验证码是否正确",
                                tags = {"验证码"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> captchaRoutes(CaptchaHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/auth/captcha", handler::generate)
                .POST("/api/v1/auth/captcha/verify", contentType(MediaType.APPLICATION_JSON), handler::verify)
                .build();
    }
}
