package com.apex.scaffold.bootall;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 组合部署入口（all-in-one 拓扑，方案 M3 / 批次 C9、H2）。
 *
 * <p>为每个启用的业务域创建独立子上下文：各自的数据源、事务、MapperScan、安全链与端口，
 * 父上下文只装配 JVM 级共享资源。单进程不等于单上下文，也不承诺单端口。
 *
 * <p>启用域由 {@code apex.boot-all.domains}（逗号分隔）声明；未声明时不启动任何域，避免
 * 默认把所有业务库塞进同一上下文造成装配冲突。拆分部署仍是生产默认形态。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.1
 */
public final class BootAllApplication {

    private BootAllApplication() {}

    /**
     * 启动组合部署应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        List<BootAllDomains.Domain> domains = BootAllDomains.resolve(System.getProperty("apex.boot-all.domains", ""));
        if (domains.isEmpty()) {
            throw new IllegalStateException("组合部署至少需要声明一个域（apex.boot-all.domains）");
        }
        ConfigurableApplicationContext parent = new SpringApplicationBuilder(BootAllParentConfiguration.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run(args);
        List<ConfigurableApplicationContext> children = new ArrayList<>();
        try {
            for (BootAllDomains.Domain domain : domains) {
                children.add(new SpringApplicationBuilder(BootAllDomainConfiguration.class)
                        .parent(parent)
                        .properties(BootAllDomains.propertiesFor(domain))
                        .run(args));
            }
            Runtime.getRuntime()
                    .addShutdownHook(new Thread(() -> children.forEach(ConfigurableApplicationContext::close)));
        } catch (RuntimeException error) {
            children.forEach(ConfigurableApplicationContext::close);
            parent.close();
            throw error;
        }
    }
}
