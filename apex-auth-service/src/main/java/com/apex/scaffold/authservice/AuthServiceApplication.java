package com.apex.scaffold.authservice;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

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
 * <h2>微服务治理（迁移方案 Phase 2）</h2>
 * <ul>
 *   <li>{@link EnableDiscoveryClient}：将本服务注册到 Nacos，服务名
 *       {@code apex-auth-service}，供网关按 {@code lb://apex-auth-service} 路由与发现。</li>
 *   <li>{@link EnableScheduling}：启用定时任务，驱动 {@code JwtKeyRotationService}
 *       的 {@code @Scheduled} 密钥轮换（每日凌晨 2 点），确保 RSA 密钥定期轮换。</li>
 * </ul>
 *
 * <h2>数据访问组件扫描</h2>
 * <p>Spring Boot 的 MyBatis-Plus 自动装配仅扫描启动类所在包（{@code com.apex.scaffold.authservice}）。
 * 而本服务的 Mapper 与 R2DBC 仓储位于库模块 {@code apex-auth}（{@code com.apex.scaffold.auth.*}），
 * 因此需显式声明扫描范围：</p>
 * <ul>
 *   <li>{@link MapperScan}：扫描认证库（{@code apex-auth}）中的 {@code @Mapper} 接口；
 *       借助 {@code annotationClass = Mapper.class} 仅注册标注 {@link Mapper} 的接口，
 *       避免将仓库/服务等普通接口误注册为 Mapper。</li>
 *   <li>{@link EnableR2dbcRepositories}：扫描认证库中的响应式 R2DBC 仓储。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@SpringBootApplication(
        scanBasePackages = {"com.apex.scaffold.authservice", "com.apex.scaffold.auth", "com.apex.scaffold.common"})
@MapperScan(basePackages = "com.apex.scaffold.auth.infrastructure.persistence.mapper", annotationClass = Mapper.class)
@EnableR2dbcRepositories(basePackages = "com.apex.scaffold.auth.infrastructure.repository")
@EnableDiscoveryClient
@EnableScheduling
public class AuthServiceApplication {

    /**
     * 认证中心独立启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
