package com.apex.scaffold.bootall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/**
 * 组合部署装配完整性测试（方案 M3 / 批次 C9）。
 *
 * <p>验证 all-in-one 拓扑的装配基础：六个业务库的组件入口均随依赖被自动装配注册，
 * 且各组件核心类（domain/application/infrastructure）在类路径可见。
 *
 * <p>不加载完整 Spring 上下文（需要 H2/Redis 等运行设施），只验证装配前提：
 * 组件入口注册文件存在、组件核心类可加载。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("组合部署装配完整性测试")
class BootAllAssemblyTest {

    /** 各业务库组件入口类（C7 交付，经 AutoConfiguration.imports 自动装配）。 */
    private static final String[] COMPONENT_ENTRIES = {
        "com.apex.scaffold.autoconfigure.SystemComponentAutoConfiguration",
        "com.apex.scaffold.auth.autoconfigure.AuthComponentAutoConfiguration",
        "com.apex.scaffold.workflow.autoconfigure.WorkflowComponentAutoConfiguration",
        "com.apex.scaffold.file.autoconfigure.FileComponentAutoConfiguration",
        "com.apex.scaffold.office.autoconfigure.OfficeComponentAutoConfiguration",
        "com.apex.scaffold.integration.connector.autoconfigure.IntegrationComponentAutoConfiguration",
        "com.apex.scaffold.integration.device.autoconfigure.IotDeviceComponentAutoConfiguration",
        "com.apex.scaffold.integration.device.autoconfigure.IotMqttAutoConfiguration"
    };

    /** 各业务库代表性实现类（组件核心可见性）。 */
    private static final String[] COMPONENT_CORE_CLASSES = {
        "com.apex.scaffold.application.service.UserService",
        "com.apex.scaffold.auth.application.service.AuthenticationApplicationService",
        "com.apex.scaffold.workflow.application.service.WorkflowService",
        "com.apex.scaffold.file.application.service.FileService",
        "com.apex.scaffold.office.application.OfficeMessageService",
        "com.apex.scaffold.integration.connector.application.ConnectorInvokeService",
        "com.apex.scaffold.integration.device.application.DeviceTelemetryService"
    };

    @Test
    @DisplayName("七个业务库组件入口类在组合包类路径可见")
    void componentEntriesAreOnClasspath() throws Exception {
        for (String entry : COMPONENT_ENTRIES) {
            Class<?> type = Class.forName(entry);
            assertThat(type).as("组件入口: " + entry).isNotNull();
        }
    }

    @Test
    @DisplayName("业务库组件核心实现类在组合包类路径可见")
    void componentCoreClassesAreOnClasspath() throws Exception {
        for (String core : COMPONENT_CORE_CLASSES) {
            Class<?> type = Class.forName(core);
            assertThat(type).as("组件核心: " + core).isNotNull();
        }
    }

    @Test
    @DisplayName("组合包自动装配清单包含各业务库组件入口")
    void autoConfigurationImportsDeclareComponents() throws Exception {
        java.util.List<String> declared = new java.util.ArrayList<>();
        for (java.net.URL url : java.util.Collections.list(getClass()
                .getClassLoader()
                .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))) {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(url.openStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(declared::add);
            }
        }
        assertThat(declared).contains(COMPONENT_ENTRIES);
    }

    @Test
    @DisplayName("组件核心包内存在 Spring 组件（装配前提）")
    void componentPackagesContainSpringBeans() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        for (String[] root : new String[][] {
            {"com.apex.scaffold.auth"},
            {"com.apex.scaffold.workflow"},
            {"com.apex.scaffold.integration.connector"},
            {"com.apex.scaffold.integration.device"}
        }) {
            java.util.Set<BeanDefinition> found = scanner.findCandidateComponents(root[0]);
            assertThat(found).as("扫描根: " + root[0]).isNotEmpty();
        }
    }
}
