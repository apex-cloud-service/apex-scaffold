package com.apex.scaffold.workflowservice;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 工作流服务启动类
 *
 * <p>工作流服务的Spring Boot应用程序入口，独立承载工作流能力（启动类、Router/Handler、
 * 独立 {@code apex_workflow} 库迁移、审批事件发布）。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link EnableDiscoveryClient}：将本服务注册到 Nacos，服务名
 *       {@code apex-workflow-service}，供网关按 {@code lb://apex-workflow-service} 路由与发现。</li>
 *   <li>{@link EnableScheduling}：启用定时任务，驱动 {@code WorkflowTimeoutHandler}
 *       的 {@code @Scheduled} 任务超时处理。</li>
 *   <li>扫描包同时覆盖 {@code com.apex.scaffold.workflow}（工作流领域/应用层 Bean）与
 *       {@code com.apex.scaffold.common}（统一响应/异常/工具组件）。</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>Spring Boot 的 MyBatis-Plus 自动装配仅扫描启动类所在包（{@code com.apex.scaffold.workflowservice}）。
 * 而本服务的 Mapper 位于库模块 {@code apex-workflow}（{@code com.apex.scaffold.workflow.*}），
 * 因此需借助 {@link MapperScan} 显式声明扫描范围；{@code annotationClass = Mapper.class}
 * 确保仅注册标注 {@link Mapper} 的接口，避免误注册其他普通接口。</p>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.apex.scaffold.workflowservice",
            "com.apex.scaffold.workflow",
            "com.apex.scaffold.common"
        })
@MapperScan(
        basePackages = "com.apex.scaffold.workflow.infrastructure.persistence.mapper",
        annotationClass = Mapper.class)
@EnableDiscoveryClient
@EnableScheduling
public class WorkflowServiceApplication {

    /**
     * 工作流服务独立启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkflowServiceApplication.class, args);
    }
}
