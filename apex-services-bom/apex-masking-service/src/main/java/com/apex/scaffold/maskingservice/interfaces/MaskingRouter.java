package com.apex.scaffold.maskingservice.interfaces;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 脱敏服务的函数式路由，OpenAPI 契约通过处理方法绑定发现。
 *
 * <p>本服务无持久化：脱敏策略来自启动配置 {@code apex.masking}，不提供规则的
 * 增删改或热刷新；租户身份只取自已验证 JWT 的 {@code tenant_id} 声明，
 * 不接受请求头或请求体指定租户。网关按 {@code masking-domain} 路由转发，
 * 是可选部署的服务增强组件。
 *
 * <p>仅暴露两个端点：批量脱敏（写语义的幂等计算）与只读规则查询。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MaskingRouter {
    /**
     * 注册批量脱敏接口与只读规则查询接口。
     *
     * <p>{@code POST /api/v1/masking/batch} 按请求中的版本号选择固定策略，
     * 版本未授权或租户不匹配时返回 403；{@code GET /api/v1/masking/rules}
     * 仅返回当前租户有权访问的版本与字段规则。
     *
     * @param handler 脱敏处理器，负责租户提取、参数校验与固定错误码映射
     * @return 脱敏路由函数，挂载于 {@code /api/v1/masking}
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/masking/batch",
                method = RequestMethod.POST,
                beanClass = MaskingHandler.class,
                beanMethod = "batch"),
        @RouterOperation(
                path = "/api/v1/masking/rules",
                method = RequestMethod.GET,
                beanClass = MaskingHandler.class,
                beanMethod = "rules")
    })
    public RouterFunction<ServerResponse> maskingRoutes(MaskingHandler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/masking/batch", handler::batch)
                .GET("/api/v1/masking/rules", handler::rules)
                .build();
    }

    /**
     * 注册 bearer 鉴权方案，供 OpenAPI 文档描述 JWT 认证方式。
     *
     * <p>只登记 {@code bearerAuth} 安全方案，不在此设置全局 {@code SecurityRequirement}；
     * 具体端点是否要求认证由各 Handler 方法上的 {@code @Operation.security} 声明，
     * 因此新增端点时必须自行标注，否则文档不会体现其鉴权要求。
     *
     * @return 仅包含安全方案组件的 OpenAPI 定义
     */
    @Bean
    public io.swagger.v3.oas.models.OpenAPI maskingOpenApi() {
        return new io.swagger.v3.oas.models.OpenAPI()
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
