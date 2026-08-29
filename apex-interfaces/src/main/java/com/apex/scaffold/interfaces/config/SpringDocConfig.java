package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.common.constant.SystemConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

/**
 * SpringDoc OpenAPI 配置
 *
 * <p>提供 Swagger UI 在线接口文档支持。
 *
 * <h2>函数式接口文档增强</h2>
 * <p>通过 {@link OpenApiDocumentationCustomizer} 补充函数式 Handler 无法由方法签名推断的
 * 请求 DTO、查询参数、响应模型、媒体类型和错误状态，并保持安全白名单与 Bearer JWT 声明一致。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class SpringDocConfig {

    /** 应用名称（兜底默认值与应用名 apex-scaffold 保持一致） */
    @Value("${spring.application.name:apex-scaffold}")
    private String applicationName;

    /** 应用版本（三档构建时由 apex-build.properties 注入；兜底默认值随发布版本同步） */
    @Value("${apex.version:1.2.0}")
    private String applicationVersion;

    private static final String LOCAL_SERVER_URL = "http://localhost:8080";
    private static final String PROD_SERVER_URL = "https://api.apex-scaffold.com";
    private static final String BEARER_AUTH = "BearerAuth";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 构造全局 OpenAPI 对象
     *
     * <p>配置Swagger文档的全局信息，包括标题、版本、联系方式、服务器地址等。
     *
     * @return 全局OpenAPI配置对象
     */
    @Bean
    public OpenAPI apexOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Apex Scaffold API 文档")
                        .description("Apex Scaffold 后端服务 API 在线文档")
                        .version(applicationVersion)
                        .contact(new Contact().name("Apex Team").email("team@apex-scaffold.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Apex Scaffold 项目仓库")
                        .url("https://github.com/apex-scaffold/apex-scaffold"))
                .servers(List.of(
                        new Server().url(LOCAL_SERVER_URL).description("本地开发环境"),
                        new Server().url(PROD_SERVER_URL).description("生产环境")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * OpenAPI 自定义器，完善 RouterFunction 生成的接口信息。
     *
     * <p>当前职责：</p>
     * <ul>
     *   <li>公开路径清除 Bearer JWT 安全要求；</li>
     *   <li>修补 RouterFunction 模式下缺失的 parameter schema（WebFlux 参数类型推断局限）；</li>
     *   <li>委托 {@link OpenApiDocumentationCustomizer} 完成剩余的请求体、查询参数、响应体补全。</li>
     * </ul>
     *
     * @return OpenApiCustomizer
     */
    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            if (openApi == null || openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (pathItem == null) {
                    return;
                }
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    if (operation == null) {
                        return;
                    }
                    if (isPublicPath(path)) {
                        operation.setSecurity(List.of());
                    }
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(parameter -> {
                            if (parameter == null) {
                                return;
                            }
                            try {
                                if (parameter.getSchema() == null) {
                                    parameter.setSchema(new io.swagger.v3.oas.models.media.StringSchema());
                                }
                            } catch (Exception ignored) {
                                // 忽略参数 schema 修复异常
                            }
                        });
                    }
                });
            });
            new OpenApiDocumentationCustomizer(this::isPublicPath).customise(openApi);
        };
    }

    /**
     * 判断接口路径是否属于安全配置中的匿名白名单。
     *
     * @param path OpenAPI 接口路径
     * @return 匹配任一匿名白名单路径时返回 {@code true}
     */
    private boolean isPublicPath(String path) {
        return SystemConstants.PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * API 分组 - 默认分组
     *
     * @param openApiCustomizer OpenAPI 文档增强定制器，补齐函数式端点的入参和响应结构
     * @return 默认API分组配置，匹配 /api/** 路径（Actuator 端点已由 springdoc.show-actuator=false 全局禁用）
     */
    @Bean
    public GroupedOpenApi defaultApi(OpenApiCustomizer openApiCustomizer) {
        return GroupedOpenApi.builder()
                .group("default")
                .pathsToMatch("/api/**")
                .addOpenApiCustomizer(openApiCustomizer)
                .build();
    }

    /**
     * API 分组 - 认证模块
     *
     * @param openApiCustomizer OpenAPI 文档增强定制器，补齐函数式端点的入参和响应结构
     * @return 认证模块API分组配置，匹配 /api/v1/auth/** 路径
     */
    @Bean
    public GroupedOpenApi authApi(OpenApiCustomizer openApiCustomizer) {
        return GroupedOpenApi.builder()
                .group("auth")
                .pathsToMatch("/api/v1/auth/**")
                .addOpenApiCustomizer(openApiCustomizer)
                .build();
    }

    /**
     * API 分组 - 用户模块
     *
     * @param openApiCustomizer OpenAPI 文档增强定制器，补齐函数式端点的入参和响应结构
     * @return 用户模块API分组配置，匹配 /api/v1/user/** 和 /api/v1/profile/** 路径
     */
    @Bean
    public GroupedOpenApi userApi(OpenApiCustomizer openApiCustomizer) {
        return GroupedOpenApi.builder()
                .group("user")
                .pathsToMatch("/api/v1/user/**", "/api/v1/profile/**")
                .addOpenApiCustomizer(openApiCustomizer)
                .build();
    }

    /** API 分组 - 系统管理（菜单/字典/参数/定时任务/日志/部门/导出/在线用户/监控大盘）。 */
    @Bean
    public GroupedOpenApi adminSystemApi(OpenApiCustomizer openApiCustomizer) {
        return GroupedOpenApi.builder()
                .group("admin-system")
                .pathsToMatch(
                        "/admin-api/v1/menus/**",
                        "/admin-api/v1/dict/**",
                        "/admin-api/v1/configs/**",
                        "/admin-api/v1/jobs/**",
                        "/admin-api/v1/login-logs/**",
                        "/admin-api/v1/audit-logs/**",
                        "/admin-api/v1/api-call-logs/**",
                        "/admin-api/v1/depts/**",
                        "/admin-api/v1/export/**",
                        "/admin-api/v1/online-users/**",
                        "/admin-api/v1/monitor/**",
                        "/api/v1/auth/captcha/**")
                .addOpenApiCustomizer(openApiCustomizer)
                .build();
    }

    /** API 分组 - 后台用户管理。 */
    @Bean
    public GroupedOpenApi adminUserApi(OpenApiCustomizer openApiCustomizer) {
        return GroupedOpenApi.builder()
                .group("admin-user")
                .pathsToMatch("/admin-api/v1/users/**", "/admin-api/v1/staff-roles/**")
                .addOpenApiCustomizer(openApiCustomizer)
                .build();
    }
}
