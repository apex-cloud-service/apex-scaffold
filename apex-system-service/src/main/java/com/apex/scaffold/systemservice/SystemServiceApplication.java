package com.apex.scaffold.systemservice;

import com.apex.scaffold.LogDirectoryConfigurer;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统服务启动类（Phase 6 系统能力收敛）。
 *
 * <p>微服务化改造 Phase 6：将系统能力（用户/RBAC/菜单/部门/字典/配置/日志/
 * 定时任务/租户/SSO/OAuth2/代码生成/监控大盘…）与由 {@code ApiRouter} 按子域
 * 拆分而来的系统路由、处理器、过滤器、安全装配收敛为独立可启动/发布/扩展的服务
 * {@code apex-system-service}（端口 8082 / 独立 {@code apex_system} 库）。
 *
 * <h2>设计说明</h2>
 * <p>本服务复用系统四大分层库模块的既有 Bean，仅新增独立启动/装配：
 * <ul>
 *   <li>{@code com.apex.scaffold.interfaces} - 系统 Web 层（Router/Handler/Filter/Security/Config）</li>
 *   <li>{@code com.apex.scaffold.application} - 应用层服务（user/rbac/config/log/job/…）</li>
 *   <li>{@code com.apex.scaffold.domain} - 领域模型/仓储契约/域服务</li>
 *   <li>{@code com.apex.scaffold.infrastructure} - 持久化/缓存/远程/事件/消息</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 *   <li>{@code com.apex.scaffold.systemservice} - 本服务自身装配</li>
 * </ul>
 *
 * <p><b>扫描边界</b>：刻意<b>不</b>扫描根包 {@code com.apex.scaffold}，
 * 以保持装配边界精确（旧单体启动类 {@code com.apex.scaffold.Application} 已随单体退役移除，
 * 相关日志目录配置仅在 main 入口显式调用）。
 *
 * <h2>设计说明</h2>
 * <p>Spring Boot 的 MyBatis-Plus 自动装配仅扫描启动类所在包
 * （{@code com.apex.scaffold.systemservice}），而系统 Mapper 位于基础设施库
 * {@code apex-infrastructure}（{@code com.apex.scaffold.infrastructure.*}），
 * 故借助 {@link MapperScan} 显式声明扫描范围；{@code annotationClass = Mapper.class}
 * 确保仅注册标注 {@link Mapper} 的接口，避免误注册其他普通接口。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.apex.scaffold.systemservice",
            "com.apex.scaffold.interfaces",
            "com.apex.scaffold.application",
            "com.apex.scaffold.domain",
            "com.apex.scaffold.infrastructure",
            "com.apex.scaffold.common"
        })
@ConfigurationPropertiesScan(basePackages = "com.apex.scaffold")
@MapperScan(
        basePackages = {"com.apex.scaffold.infrastructure.persistence", "com.apex.scaffold.infrastructure.outbox"},
        annotationClass = Mapper.class)
@EnableAsync
@EnableDiscoveryClient
@EnableScheduling
public class SystemServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        LogDirectoryConfigurer.configure();
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
