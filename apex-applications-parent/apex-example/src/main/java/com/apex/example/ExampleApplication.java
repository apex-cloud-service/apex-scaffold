package com.apex.example;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 示例应用启动类
 *
 * <p>展示如何独立运行示例模块。本模块同时是 basic/示例档与 k8s 单体形态的
 * 聚合运行入口：镜像构建经 Containerfile 以 {@code SERVICE_MODULE=apex-example}
 * 产出 {@code apex-scaffold:latest}（口径见 apex-tools-build 模块注册表）；
 * “example” 名称保留为交付兼容（2026-11-07 模块评审 F10 处置）。
 *
 * <p>MyBatis-Plus 自动装配仅扫描启动类所在包（{@code com.apex.example}），
 * 而示例复用的 Mapper 分散在各库模块（{@code com.apex.scaffold.*}，
 * 如 infrastructure / workflow 等），故显式 {@link MapperScan} 声明扫描范围，
 * 并以 {@code annotationClass = Mapper.class} 限定仅注册 {@code @Mapper} 接口，
 * 与各微服务启动类保持一致的装配方式；注意显式 {@link MapperScan}
 * 会关闭 MyBatis-Plus 的默认自动扫描，因此需同时纳入示例自身的 Mapper 包
 * （{@code com.apex.example.repository}）。
 *
 * <p>组件扫描覆盖 {@code com.apex.scaffold}，同样需以
 * {@link ConfigurationPropertiesScan} 注册该包下的 {@code @ConfigurationProperties}
 * Bean（如 {@code GovernanceProperties}），否则其依赖方装配失败。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.apex.example", "com.apex.scaffold"})
@ConfigurationPropertiesScan(basePackages = "com.apex.scaffold")
@MapperScan(
        basePackages = {"com.apex.example.repository", "com.apex.scaffold"},
        annotationClass = Mapper.class)
public class ExampleApplication {

    /**
     * 应用启动入口
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Apex Scaffold 示例应用启动成功!");
        System.out.println("API文档: http://localhost:8080/doc.html");
        System.out.println("健康检查: http://localhost:8080/api/health");
        System.out.println("========================================\n");
    }
}
