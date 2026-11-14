package com.apex.scaffold.fileservice;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件服务启动类。
 *
 * <p>微服务化改造 Phase 4：将文件能力（文件上传/下载、文档、图表、大文件分片与预览）
 * 从单体中抽取为独立可启动/发布/扩展的服务 {@code apex-file-service}（端口 8085）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@code com.apex.scaffold.fileservice} - 本服务接口层/配置/安全</li>
 *   <li>{@code com.apex.scaffold.file} - 文件领域/应用/基础设施模块（apex-file）</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>Spring Boot 的 MyBatis-Plus 自动装配仅扫描启动类所在包（{@code com.apex.scaffold.fileservice}）。
 * 而本服务的 Mapper 位于库模块 {@code apex-file}（{@code com.apex.scaffold.file.*}），
 * 因此需借助 {@link MapperScan} 显式声明扫描范围；{@code annotationClass = Mapper.class}
 * 确保仅注册标注 {@link Mapper} 的接口，避免误注册其他普通接口。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(
        scanBasePackages = {"com.apex.scaffold.fileservice", "com.apex.scaffold.file", "com.apex.scaffold.common"})
@MapperScan(basePackages = "com.apex.scaffold.file.infrastructure.persistence.mapper", annotationClass = Mapper.class)
@EnableDiscoveryClient
@EnableScheduling
public class FileServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
