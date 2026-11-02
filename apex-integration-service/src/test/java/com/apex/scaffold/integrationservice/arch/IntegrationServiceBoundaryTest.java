package com.apex.scaffold.integrationservice.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.integrationservice.IntegrationServiceApplication;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成服务模块边界与启动装配契约守卫测试。
 *
 * <p>以零依赖（不启动 Spring 上下文、不连接 MySQL/Redis）的确定性断言，锁定
 * {@code apex-integration-service} 的三项关键契约，防止微服务化成果被无意识回退：</p>
 *
 * <h2>覆盖的回归场景</h2>
 * <ul>
 *   <li><b>模块边界</b>：本服务 classpath 不得混入已拆出的兄弟服务入口类
 *       （系统 / 认证 / 工作流 / 文件 / 办公）。任一入口类可被加载，即说明依赖被
 *       重新耦合，微服务边界被破坏。</li>
 *   <li><b>启动装配契约</b>：{@link IntegrationServiceApplication} 只扫描自身包与
 *       common，不组件扫描 {@code com.apex.scaffold.integration} 根包——组件库经
 *       {@code AutoConfiguration.imports} 自动装配，且 connector interfaces 装配
 *       无独立条件注解，仅靠组件入口的开关守护；组件扫描根包会绕过
 *       {@code apex.component.integration.interfaces-enabled} 开关并造成重复装配。
 *       Mapper 注册改由 {@link MapperScan} 限定 {@code @Mapper} 接口完成。</li>
 *   <li><b>组件入口注册</b>：integration 三个组件入口（connector / device / MQTT 桥接）
 *       在类路径可见，且在 {@code AutoConfiguration.imports} 清单中声明。</li>
 * </ul>
 *
 * <p>本测试不启动容器，故不依赖外部基础设施；上下文级装配验证由 boot-all
 * 组合部署与在线集成测试覆盖。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("集成服务边界与启动装配契约测试")
class IntegrationServiceBoundaryTest {

    /** integration 组件入口类（C7 交付，经 AutoConfiguration.imports 自动装配）。 */
    private static final String[] INTEGRATION_COMPONENT_ENTRIES = {
        "com.apex.scaffold.integration.connector.autoconfigure.IntegrationComponentAutoConfiguration",
        "com.apex.scaffold.integration.device.autoconfigure.IotDeviceComponentAutoConfiguration",
        "com.apex.scaffold.integration.device.autoconfigure.IotMqttAutoConfiguration"
    };

    /** integration 组件代表性实现类（组件核心可见性）。 */
    private static final String[] INTEGRATION_CORE_CLASSES = {
        "com.apex.scaffold.integration.connector.application.ConnectorInvokeService",
        "com.apex.scaffold.integration.connector.application.WebhookInboundService",
        "com.apex.scaffold.integration.device.application.DeviceTelemetryService",
        "com.apex.scaffold.integration.device.application.DeviceCommandService"
    };

    /**
     * 校验集成服务 classpath 未混入已拆出的兄弟服务入口类。
     *
     * <p>五个兄弟服务均已独立成模块（各自拥有独立库、Flyway 链与网关路由）。
     * 若任一入口类在本服务 classpath 上可被加载，即意味着模块间出现编译期耦合。</p>
     */
    @Test
    void classpathDoesNotContainSiblingServiceEntrypoints() {
        assertThat(isClassPresent("com.apex.scaffold.systemservice.SystemServiceApplication"))
                .as("系统服务入口不应出现在集成服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.authservice.AuthServiceApplication"))
                .as("认证服务入口不应出现在集成服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.workflowservice.WorkflowServiceApplication"))
                .as("工作流服务入口不应出现在集成服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.fileservice.FileServiceApplication"))
                .as("文件服务入口不应出现在集成服务 classpath")
                .isFalse();
        assertThat(isClassPresent("com.apex.scaffold.officeservice.OfficeServiceApplication"))
                .as("办公服务入口不应出现在集成服务 classpath")
                .isFalse();
    }

    /**
     * 校验启动类只扫描自身包与 common，不组件扫描 integration 库根包。
     *
     * <p>{@code scanBasePackages} 缺失自身包或 common 会导致服务 Bean / 共享上下文
     * 静默缺失；而组件扫描 {@code com.apex.scaffold.integration} 根包会绕过组件库的
     * 装配开关并造成重复注册，两者都是边界回退。</p>
     */
    @Test
    void applicationDeclaresRequiredScanPackages() {
        SpringBootApplication annotation =
                IntegrationServiceApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(annotation).as("启动类必须标注 @SpringBootApplication").isNotNull();
        assertThat(annotation.scanBasePackages())
                .contains("com.apex.scaffold.integrationservice", "com.apex.scaffold.common");
        assertThat(annotation.scanBasePackages())
                .as("不得组件扫描 integration 库根包：组件经 AutoConfiguration.imports 装配，" + "且 connector interfaces 开关仅由组件入口守护")
                .doesNotContain("com.apex.scaffold.integration");
    }

    /**
     * 校验启动类通过 {@link MapperScan} 覆盖 integration 库包，且仅注册 {@link Mapper} 接口。
     */
    @Test
    void applicationScansIntegrationMappers() {
        MapperScan mapperScan = IntegrationServiceApplication.class.getAnnotation(MapperScan.class);
        assertThat(mapperScan).as("启动类必须标注 @MapperScan").isNotNull();
        assertThat(mapperScan.basePackages()).contains("com.apex.scaffold.integration");
        assertThat(mapperScan.annotationClass())
                .as("仅注册标注 @Mapper 的接口，避免误注册普通接口")
                .isEqualTo(Mapper.class);
    }

    /**
     * 校验 integration 组件入口类在类路径可见，并在自动装配清单中声明。
     *
     * <p>任一入口类缺失，对应子域（connector / device / MQTT 桥接）的 Bean 会静默缺失。</p>
     */
    @Test
    @DisplayName("integration 组件入口类可加载且在自动装配清单中声明")
    void integrationComponentEntriesDeclaredAndLoadable() throws Exception {
        for (String entry : INTEGRATION_COMPONENT_ENTRIES) {
            assertThat(isClassPresent(entry)).as("组件入口可加载: " + entry).isTrue();
        }
        List<String> declared = readAutoConfigurationImports();
        assertThat(declared)
                .as("AutoConfiguration.imports 必须声明 integration 组件入口")
                .contains(INTEGRATION_COMPONENT_ENTRIES);
    }

    /**
     * 校验 integration 组件代表性实现类在本服务类路径可见。
     */
    @Test
    void integrationCoreClassesAreOnClasspath() {
        for (String core : INTEGRATION_CORE_CLASSES) {
            assertThat(isClassPresent(core)).as("组件核心可加载: " + core).isTrue();
        }
    }

    /**
     * 读取类路径上全部 {@code AutoConfiguration.imports} 清单的有效行。
     *
     * @return 非空、非注释的声明行
     */
    private static List<String> readAutoConfigurationImports() throws Exception {
        List<String> declared = new ArrayList<>();
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        for (URL url : Collections.list(
                IntegrationServiceApplication.class.getClassLoader().getResources(resource))) {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(declared::add);
            }
        }
        return declared;
    }

    /**
     * 判断指定类是否可在集成服务的类加载器上解析（不触发静态初始化）。
     *
     * @param className 全限定类名
     * @return 可加载返回 {@code true}，否则 {@code false}
     */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, IntegrationServiceApplication.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
