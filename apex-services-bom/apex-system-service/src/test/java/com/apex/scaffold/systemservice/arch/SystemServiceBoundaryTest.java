package com.apex.scaffold.systemservice.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.systemservice.SystemServiceApplication;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统服务模块边界与启动装配契约守卫测试。
 *
 * <p>以零依赖（不启动 Spring 上下文、不连接 MySQL/Redis）的确定性断言，锁定
 * {@code apex-system-service} 的两项关键契约，防止微服务化成果被无意识回退：</p>
 *
 * <h2>覆盖的回归场景</h2>
 * <ul>
 *   <li><b>模块边界</b>：本服务 classpath 不得混入已拆出的兄弟服务入口类
 *       （办公 / 认证 / 文件 / 工作流）。任一入口类可被加载，即说明依赖被重新耦合，
 *       微服务边界被破坏（§17.2.1「可独立构建/部署」）。</li>
 *   <li><b>启动装配契约</b>：{@link SystemServiceApplication} 必须以绞杀者模式扫描
 *       系统四层库模块，并通过 {@link MapperScan} 覆盖持久化与 Outbox 包；任一扫描
 *       范围被误删，都会导致系统域 Bean / Mapper 静默缺失。</li>
 * </ul>
 *
 * <p>本测试不启动容器，故不依赖外部基础设施；上下文级装配验证仍由后续
 * Phase 6 在线集成测试覆盖。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class SystemServiceBoundaryTest {

    /**
     * 校验系统服务 classpath 未混入已拆出的兄弟服务入口类。
     *
     * <p>四个兄弟服务均已独立成模块（各自拥有独立库、Flyway 链与网关路由）。
     * 若任一入口类在本服务 classpath 上可被加载，即意味着模块间出现编译期耦合，
     * 与「服务间无编译依赖」的架构验收项冲突。</p>
     */
    @Test
    void classpathDoesNotContainSiblingServiceEntrypoints() {
        assertThat(isClassPresent("com.apex.scaffold.officeservice.OfficeServiceApplication"))
                .as("办公服务入口不应出现在系统服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.authservice.AuthServiceApplication"))
                .as("认证服务入口不应出现在系统服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.fileservice.FileServiceApplication"))
                .as("文件服务入口不应出现在系统服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.workflowservice.WorkflowServiceApplication"))
                .as("工作流服务入口不应出现在系统服务 classpath")
                .isFalse();
    }

    /**
     * 校验启动类以绞杀者模式声明了系统四层库模块的扫描范围。
     *
     * <p>{@code scanBasePackages} 缺失任一层，都会导致对应分层的 Bean 静默缺失。</p>
     */
    @Test
    void applicationDeclaresRequiredScanPackages() {
        SpringBootApplication annotation = SystemServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(annotation).as("启动类必须标注 @SpringBootApplication").isNotNull();
        assertThat(annotation.scanBasePackages())
                .contains(
                        "com.apex.scaffold.systemservice",
                        "com.apex.scaffold.interfaces",
                        "com.apex.scaffold.application",
                        "com.apex.scaffold.domain",
                        "com.apex.scaffold.infrastructure",
                        "com.apex.scaffold.common");
    }

    /**
     * 校验启动类通过 {@link MapperScan} 覆盖持久化与 Outbox 包，且仅注册 {@link Mapper} 接口。
     */
    @Test
    void applicationScansPersistenceAndOutboxMappers() {
        MapperScan mapperScan = SystemServiceApplication.class.getAnnotation(MapperScan.class);
        assertThat(mapperScan).as("启动类必须标注 @MapperScan").isNotNull();
        assertThat(mapperScan.basePackages())
                .contains("com.apex.scaffold.infrastructure.persistence", "com.apex.scaffold.infrastructure.outbox");
        assertThat(mapperScan.annotationClass())
                .as("仅注册标注 @Mapper 的接口，避免误注册普通接口")
                .isEqualTo(Mapper.class);
    }

    /**
     * 判断指定类是否可在系统服务的类加载器上解析（不触发静态初始化）。
     *
     * @param className 全限定类名
     * @return 可加载返回 {@code true}，否则 {@code false}
     */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, SystemServiceApplication.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
