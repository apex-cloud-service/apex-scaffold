package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Knife4j 增强文档集成契约测试。
 *
 * <p>验证增强接口文档（doc.html）的基础设施可用性：
 * <ul>
 *   <li>knife4j-openapi3-ui webjar 的 {@code doc.html} 存在于 classpath（纯前端 webjar，不引入 MVC 依赖）</li>
 *   <li>webjar 静态资源（/webjars/**）可由 Spring WebFlux 默认静态资源映射解析</li>
 *   <li>springdoc OpenAPI JSON 端点（/v3/api-docs）与 Swagger UI 路径配置存在</li>
 * </ul>
 *
 * <p>不启动完整应用上下文，纯 classpath 层验证，快速且无外部依赖。
 * 安全放行（/doc.html、/webjars/**）由 SystemConstants.PUBLIC_PATHS 与 LoggingFilter 保证，另有测试覆盖。
 */
class Knife4jIntegrationContractTest {

    @Test
    void knife4jDocHtmlIsPresentOnClasspath() throws Exception {
        // knife4j-openapi3-ui webjar 将 doc.html 置于 META-INF/resources/doc.html
        try (var in = new ClassPathResource("META-INF/resources/doc.html").getInputStream()) {
            String html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(html)
                    .as("doc.html 必须包含 knife4j 前端应用挂载点")
                    .contains("<div id=app>")
                    .contains("webjars/js/app.");
        }
    }

    @Test
    void knife4jWebjarAssetsAreResolvable() throws Exception {
        // doc.html 相对路径引用 webjars/... → 实际请求 /webjars/**，由 Spring Boot 静态资源映射提供
        var appJs = new ClassPathResource("META-INF/resources/webjars/js/app.b0c0d7df.js");
        assertThat(appJs.exists())
                .as("knife4j 前端 JS 资源必须可通过 /webjars/** 解析")
                .isTrue();
    }

    @Test
    void springdocEndpointsAreConfiguredInDefaultProfile() throws Exception {
        // application.yml 中 springdoc.swagger-ui.path 与 api-docs.path 为默认配置
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        var props = yaml.<java.util.Map<String, Object>>load(
                new ClassPathResource("application.yml").getInputStream());
        @SuppressWarnings("unchecked")
        var springdoc = (java.util.Map<String, Object>) props.get("springdoc");
        assertThat(springdoc).as("application.yml 必须配置 springdoc 段").isNotNull();
        @SuppressWarnings("unchecked")
        var ui = (java.util.Map<String, Object>) springdoc.get("swagger-ui");
        @SuppressWarnings("unchecked")
        var docs = (java.util.Map<String, Object>) springdoc.get("api-docs");
        assertThat(ui.get("path")).isEqualTo("/swagger-ui.html");
        assertThat(docs.get("path")).isEqualTo("/v3/api-docs");
    }

    @Test
    void knife4jIsEnabledInDevelopmentProfile() throws Exception {
        // application.yml 中 knife4j.enable=true / production=false（开发可用；prod 强制关闭）
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        var props = yaml.<java.util.Map<String, Object>>load(
                new ClassPathResource("application.yml").getInputStream());
        @SuppressWarnings("unchecked")
        var knife4j = (java.util.Map<String, Object>) props.get("knife4j");
        assertThat(knife4j).as("application.yml 必须配置 knife4j 段").isNotNull();
        assertThat(knife4j.get("enable")).isEqualTo(true);
        assertThat(knife4j.get("production")).isEqualTo(false);
    }
}
