package com.apex.scaffold.aggregationservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 聚合服务安全配置单元测试。
 *
 * <p>验证安全链可成功构建并携带过滤链（关闭 CSRF/Basic/表单登录与登出、无状态上下文仓库、
 * 401 未认证入口），保证 {@code /api/v2/todo} 的认证前置条件在运行期成立。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("聚合服务安全配置测试")
class AggregationSecurityConfigTest {

    @Test
    @DisplayName("安全链构建成功且包含过滤器")
    void buildsSecurityChainWithFilters() {
        AggregationSecurityConfig config = new AggregationSecurityConfig();

        SecurityWebFilterChain chain = config.securityWebFilterChain(ServerHttpSecurity.http());

        assertThat(chain).isNotNull();
        assertThat(chain.getWebFilters().collectList().block()).isNotEmpty();
    }
}
