package com.apex.scaffold.bootall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 组合部署入口（all-in-one 拓扑，方案 M3 / 批次 C9）。
 *
 * <p>单进程装配全部业务组件（system/auth/workflow/file/office）：组件经各自的
 * {@code AutoConfiguration} 入口自动装配（C7），本类不做 {@code scanBasePackages} 全根扫描，
 * 以验证"组件化 + 自动装配"的组合能力。
 *
 * <p>适用：basic / 示例 / 演示档单进程运行；企业部署仍为五服务拆分。
 * 组合拓扑下事件通道按 {@code apex.events.channel=auto} 自动退化为进程内派发（C8）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication
public class BootAllApplication {

    /**
     * 启动组合部署应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BootAllApplication.class, args);
    }
}
