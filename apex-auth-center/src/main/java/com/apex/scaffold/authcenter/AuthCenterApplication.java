package com.apex.scaffold.authcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证授权中心启动类
 *
 * <p>统一认证授权中心的Spring Boot应用程序入口
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>JWT Token签发、验证、刷新和撤销</li>
 *   <li>用户名密码登录认证</li>
 *   <li>会话管理和在线用户追踪</li>
 *   <li>多租户隔离支持</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@SpringBootApplication(scanBasePackages = {"com.apex.scaffold.authcenter", "com.apex.scaffold.common"})
public class AuthCenterApplication {

    /**
     * 认证中心独立启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthCenterApplication.class, args);
    }
}
