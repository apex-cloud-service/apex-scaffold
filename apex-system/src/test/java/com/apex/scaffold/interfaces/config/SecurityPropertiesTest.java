package com.apex.scaffold.interfaces.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * {@link SecurityProperties} 管理端路径配置测试。
 *
 * <p>验证所有默认管理端路径均符合 Spring WebFlux {@link PathPatternParser} 语法，避免非法通配符
 * 导致安全过滤器链初始化失败。</p>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("安全路径配置测试")
class SecurityPropertiesTest {

    /**
     * 验证默认管理端路径都能被 WebFlux 路径解析器正确解析。
     */
    @Test
    @DisplayName("默认管理端路径应符合 PathPattern 语法")
    void shouldParseAllDefaultAdminPaths() {
        SecurityProperties properties = new SecurityProperties();
        PathPatternParser parser = new PathPatternParser();

        properties
                .getAdminPaths()
                .forEach(path -> assertDoesNotThrow(() -> parser.parse(path), () -> "非法管理端路径: " + path));
    }
}
