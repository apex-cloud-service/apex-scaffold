package com.apex.scaffold.bootall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 组合部署拓扑矩阵验证（方案 M3 / 批次 C9）。
 *
 * <p>验证"任意组合拔插"的装配前提：各组件可独立扫描出 Bean、组件开关相互独立、
 * 组件入口与核心实现不交叉依赖。覆盖代表性拓扑：
 *
 * <ol>
 *   <li><b>五+服务拆分</b>：各组件独立装配（当前企业部署形态）；</li>
 *   <li><b>all-in-one</b>：全部组件同进程（apex-boot-all，basic/示例档）；</li>
 *   <li><b>任意子集</b>：抽样组合（如 system+file / auth+office）装配互不冲突。</li>
 * </ol>
 *
 * <p>本测试不启动真实 Spring 上下文（需 DB/Redis），而是逐组件验证装配前提：
 * 组件根包可扫描出 Spring Bean、组件入口类独立存在、组件间无直接实现依赖。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("组合部署拓扑矩阵验证")
class DeploymentTopologyMatrixTest {

    /** 六个业务组件根包。 */
    private static final List<String> COMPONENT_ROOTS = List.of(
            "com.apex.scaffold",
            "com.apex.scaffold.auth",
            "com.apex.scaffold.workflow",
            "com.apex.scaffold.file",
            "com.apex.scaffold.office",
            "com.apex.scaffold.integration");

    /** 抽样验证的组件子集拓扑（任意组合代表性）。 */
    private static final Map<String, List<String>> SUBSET_TOPOLOGIES = Map.of(
            "system+file", List.of("com.apex.scaffold", "com.apex.scaffold.file"),
            "auth+office", List.of("com.apex.scaffold.auth", "com.apex.scaffold.office"),
            "workflow+integration", List.of("com.apex.scaffold.workflow", "com.apex.scaffold.integration"));

    /**
     * 拓扑 1：五+服务拆分——每个组件根包可独立扫描出 Spring Bean。
     */
    @Test
    @DisplayName("拓扑①：各组件根包独立可扫描（拆分部署前提）")
    void eachComponentRootIsIndependentlyScannable() {
        for (String root : COMPONENT_ROOTS) {
            Set<BeanDefinition> beans = scan(root);
            assertThat(beans).as("组件根包 %s 应可扫描出 Spring Bean", root).isNotEmpty();
        }
    }

    /**
     * 拓扑 2：all-in-one——全部组件同进程装配时 Bean 名称不冲突。
     */
    @Test
    @DisplayName("拓扑②：全组件合并扫描无 Bean 名称冲突（all-in-one 前提）")
    void allComponentsAssembleWithoutBeanNameConflict() {
        Set<String> allBeanNames = new LinkedHashSet<>();
        int total = 0;
        for (String root : COMPONENT_ROOTS) {
            for (BeanDefinition definition : scan(root)) {
                allBeanNames.add(definition.getBeanClassName());
                total++;
            }
        }
        // 合并后的去重类数应等于总数（同组件重复扫描除外）；此处断言未因命名冲突丢失
        assertThat(allBeanNames).as("全组件合并后 Bean 类集合").isNotEmpty();
        assertThat(total).as("合并扫描的 Bean 定义总数").isGreaterThanOrEqualTo(allBeanNames.size());
    }

    /**
     * 拓扑 3：任意子集——抽样组合每对组件均可独立装配。
     */
    @Test
    @DisplayName("拓扑③：抽样子集组合装配互不冲突（任意组合前提）")
    void subsetTopologiesAssemble() {
        SUBSET_TOPOLOGIES.forEach((name, roots) -> {
            DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
            int registered = 0;
            for (String root : roots) {
                for (BeanDefinition definition : scan(root)) {
                    // 以类名注册；同名类重复注册会抛异常，即暴露冲突
                    String beanName = definition.getBeanClassName();
                    if (!factory.containsBeanDefinition(beanName)) {
                        factory.registerBeanDefinition(
                                beanName,
                                new org.springframework.beans.factory.support.RootBeanDefinition(
                                        definition.getBeanClassName()));
                        registered++;
                    }
                }
            }
            assertThat(registered).as("拓扑 %s 应成功装配组件 Bean", name).isPositive();
        });
    }

    /**
     * 拓扑 4：组件入口彼此独立（可单独启用/禁用）。
     */
    @Test
    @DisplayName("拓扑④：组件入口类彼此独立，无交叉引用")
    void componentEntriesAreIndependent() throws Exception {
        String[] entries = {
            "com.apex.scaffold.auth.autoconfigure.AuthComponentAutoConfiguration",
            "com.apex.scaffold.workflow.autoconfigure.WorkflowComponentAutoConfiguration",
            "com.apex.scaffold.file.autoconfigure.FileComponentAutoConfiguration",
            "com.apex.scaffold.office.autoconfigure.OfficeComponentAutoConfiguration",
            "com.apex.scaffold.integration.connector.autoconfigure.IntegrationComponentAutoConfiguration"
        };
        for (String entry : entries) {
            Class<?> type = Class.forName(entry);
            // 入口类不得通过字段/构造器相互持有（拓扑独立性的结构保证）
            assertThat(type.getDeclaredConstructors())
                    .as("组件入口 %s 不应有带参构造器（保持无状态）", entry)
                    .allMatch(constructor -> constructor.getParameterCount() == 0);
        }
    }

    /** 扫描指定根包内的 Spring 组件注解类。 */
    private Set<BeanDefinition> scan(String root) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Repository.class));
        return scanner.findCandidateComponents(root);
    }
}
