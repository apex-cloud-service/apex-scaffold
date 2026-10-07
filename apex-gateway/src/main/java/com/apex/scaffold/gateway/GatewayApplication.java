package com.apex.scaffold.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Apex Scaffold - API 网关启动类.
 *
 * <p>职责边界：
 * <ul>
 *   <li>统一入口与路由分发（Spring Cloud Gateway + Nacos lb://）</li>
 *   <li>JWT 验签与用户上下文注入（只验签、不查库，廉价快速拒绝无效请求）</li>
 *   <li>全局限流与链路追踪入口</li>
 * </ul>
 *
 * <p><b>不含</b>业务逻辑：权限判定、会话与黑名单校验由各业务服务完成（深度防御）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    /**
     * 网关进程入口.
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
