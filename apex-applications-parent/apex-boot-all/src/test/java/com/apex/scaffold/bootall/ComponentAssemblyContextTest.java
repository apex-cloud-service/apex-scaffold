package com.apex.scaffold.bootall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

/**
 * 组件入口注册检查。
 *
 * <p>验证组件自动装配入口确实声明在 {@code AutoConfiguration.imports} 中，并且入口条件
 * 与扫描范围符合约定。完整业务域上下文还需要各域 MapperScan、数据库与外部设施，
 * 由拆分服务启动与部署阶段验证，本测试不把它伪装成“已可运行”。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("组件入口注册")
class ComponentAssemblyContextTest {

    @Test
    @DisplayName("七个业务库组件入口均应声明在自动装配清单")
    void componentEntriesDeclaredInImports() {
        java.util.List<String> declared = ImportCandidates.load(
                        AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        assertThat(declared)
                .contains(
                        "com.apex.scaffold.autoconfigure.SystemComponentAutoConfiguration",
                        "com.apex.scaffold.auth.autoconfigure.AuthComponentAutoConfiguration",
                        "com.apex.scaffold.workflow.autoconfigure.WorkflowComponentAutoConfiguration",
                        "com.apex.scaffold.file.autoconfigure.FileComponentAutoConfiguration",
                        "com.apex.scaffold.office.autoconfigure.OfficeComponentAutoConfiguration",
                        "com.apex.scaffold.integration.connector.autoconfigure.IntegrationComponentAutoConfiguration",
                        "com.apex.scaffold.integration.device.autoconfigure.IotDeviceComponentAutoConfiguration");
    }

    @Test
    @DisplayName("业务库不应再声明不存在的 interfaces 扫描目录")
    void businessLibrariesHaveNoInterfacesDirectory() {
        // 嵌套布局：业务库位于 apex-components-parent/<module>；先自检业务库源码目录真实存在，
        // 再断言其 interfaces 目录不存在——防止路径漂移时守护面静默缩水。
        for (String domain : new String[] {"auth", "workflow", "file", "office"}) {
            java.nio.file.Path librarySources = java.nio.file.Path.of(
                    "..",
                    "..",
                    "apex-components-parent",
                    "apex-" + domain,
                    "src/main/java/com/apex/scaffold/" + domain);
            assertThat(java.nio.file.Files.isDirectory(librarySources))
                    .as("业务库源码目录必须存在（守护面自检）：" + domain)
                    .isTrue();
            assertThat(java.nio.file.Files.exists(librarySources.resolve("interfaces")))
                    .as("业务库协议层应归属运行模块：" + domain)
                    .isFalse();
        }
    }
}
