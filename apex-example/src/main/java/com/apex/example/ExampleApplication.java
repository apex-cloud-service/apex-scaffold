package com.apex.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例应用启动类
 *
 * <p>展示如何独立运行示例模块
 *
 * @author Apex Team
 * @version 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.apex.example", "com.apex.scaffold"})
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Apex Scaffold 示例应用启动成功!");
        System.out.println("API文档: http://localhost:8080/doc.html");
        System.out.println("健康检查: http://localhost:8080/api/health");
        System.out.println("========================================\n");
    }
}
