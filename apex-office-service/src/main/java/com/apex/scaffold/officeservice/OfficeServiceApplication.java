package com.apex.scaffold.officeservice;

import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 办公服务启动类（Phase 5 骨架占位）。
 *
 * <p>微服务化改造 Phase 5：将办公能力（组织 / 审批 / 日历 / 消息 / 三平台同步）
 * 从单体抽取为独立可启动/发布/扩展的服务 {@code apex-office-service}（端口 8084）。
 *
 * <h2>扫描范围</h2>
 * <ul>
 *   <li>{@code com.apex.scaffold.officeservice} - 本服务接口层/配置/安全</li>
 *   <li>{@code com.apex.scaffold.office} - 办公领域/应用模块（apex-office）</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 * </ul>
 *
 * <p><b>注意</b>：Phase 5 迁移完成前，办公应用层尚依赖单体系统类，需先经
 * apex-api-contract 内部契约桥接后方可实际编译运行。
 *
 * <h2>数据访问组件扫描</h2>
 * <p>Spring Boot 的 MyBatis-Plus 自动装配仅扫描启动类所在包（{@code com.apex.scaffold.officeservice}）。
 * 而本服务的 Mapper 位于库模块 {@code apex-office}（{@code com.apex.scaffold.office.*}），
 * 且分散于 {@code infrastructure.mapper} 与 {@code infrastructure.persistence(.mapper)} 等子包，
 * 因此需借助 {@link MapperScan} 以父包 {@code com.apex.scaffold.office.infrastructure} 显式声明扫描范围；
 * {@code annotationClass = Mapper.class} 确保仅注册标注 {@link Mapper} 的接口，避免误注册其他普通接口。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(
        scanBasePackages = {"com.apex.scaffold.officeservice", "com.apex.scaffold.office", "com.apex.scaffold.common"})
@EnableConfigurationProperties(OfficePlatformProperties.class)
@MapperScan(
        basePackages = {"com.apex.scaffold.office.infrastructure", "com.apex.scaffold.office.off.infrastructure"},
        annotationClass = Mapper.class)
@EnableDiscoveryClient
@EnableScheduling
public class OfficeServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OfficeServiceApplication.class, args);
    }
}
