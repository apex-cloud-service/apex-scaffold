package com.apex.scaffold.aggregationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 查询聚合服务启动类（S4-c/S4-d）。
 *
 * <p>跨源统一待办只读聚合运行模块，默认端口 8098。纯只读：无数据库、无 MyBatis、
 * 无 Flyway、无 Seata、无 job starter；仅依赖 WebFlux 与共享通用能力。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@SpringBootApplication(scanBasePackages = {"com.apex.scaffold.aggregationservice", "com.apex.scaffold.common"})
@EnableDiscoveryClient
public class AggregationServiceApplication {

    /**
     * 启动查询聚合服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}
