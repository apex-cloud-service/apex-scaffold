package com.apex.scaffold.integrationservice;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 集成服务启动类（方案 M5 / 批次 E4）。
 *
 * <p>将集成能力（连接器配置与出站调用 / 凭据管理 / webhook 入站 / 物联网设备注册、
 * 遥测、命令与轻量规则）从业务库 {@code apex-integration} 独立为可启动/发布/扩展的服务
 * {@code apex-integration-service}（端口 8094 / 独立 {@code apex_integration} 库）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@code com.apex.scaffold.integrationservice} - 本服务安全配置与事件装配</li>
 *   <li>{@code com.apex.scaffold.common} - 共享基础组件</li>
 * </ul>
 *
 * <h2>设计说明（与 office/file 等服务的差异）</h2>
 * <p>{@code apex-integration} 组件库已发布 {@code AutoConfiguration.imports} 组件入口
 * （connector / IoT device / MQTT 三个自动装配），本启动类<b>刻意不</b>把
 * {@code com.apex.scaffold.integration} 放入 {@code scanBasePackages}：
 * <ul>
 *   <li>组件根包扫描会绕过 connector 子域的 {@code apex.component.integration.interfaces-enabled}
 *       开关（connector interfaces 类自身无独立条件注解，仅由组件入口的嵌套装配守护；
 *       device 子域 interfaces 类虽自带条件，双路径装配仍属重复）。</li>
 *   <li>组件装配全部经 imports 入口完成，与 apex-boot-all 组合拓扑行为一致。</li>
 * </ul>
 * Mapper 不在自动装配范围内，经 {@link MapperScan} 以域根包声明；
 * {@code annotationClass = Mapper.class} 确保仅注册标注 {@link Mapper} 的接口。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(scanBasePackages = {"com.apex.scaffold.integrationservice", "com.apex.scaffold.common"})
@MapperScan(basePackages = "com.apex.scaffold.integration", annotationClass = Mapper.class)
@EnableDiscoveryClient
@EnableScheduling
public class IntegrationServiceApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IntegrationServiceApplication.class, args);
    }
}
