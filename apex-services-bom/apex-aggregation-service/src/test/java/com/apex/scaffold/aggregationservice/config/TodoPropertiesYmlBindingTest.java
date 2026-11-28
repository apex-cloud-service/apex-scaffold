package com.apex.scaffold.aggregationservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/**
 * {@link TodoProperties} 与 {@code application.yml} 的绑定一致性回归。
 *
 * <p>历史缺陷（BE-068）：{@code todo} 配置块曾挂在 {@code aggregation.*} 前缀下，而
 * {@code TodoProperties} 绑定 {@code apex.todo}，整块配置不会被绑定、运行默认来自代码。
 * 由于 yml 字面值（环境变量占位符的默认值）恰与代码默认一致，本测试以「哨兵值」预置实例，
 * 再从服务真实 {@code application.yml}（经占位符解析，环境变量缺省时取 {@code :默认值}）
 * 经 {@link Binder} 绑定：键真实生效时哨兵值被覆盖为 yml 值，借此同时防住
 * 「前缀漂移」与「绑定静默失效」两类回归。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class TodoPropertiesYmlBindingTest {

    /**
     * 构造全字段为哨兵值的实例（与代码默认、yml 默认均不同），用于证明绑定真实发生。
     *
     * @return 哨兵实例
     */
    private TodoProperties sentinelInstance() {
        TodoProperties properties = new TodoProperties();
        properties.setSources(new LinkedHashMap<>());
        properties.setPageSize(-1);
        properties.setApprovalLimit(-1);
        properties.setMessageLimit(-1);
        properties.setTimeoutMillis(-1L);
        properties.setDefaultBulkheadMaxConcurrency(-1);
        return properties;
    }

    /**
     * 基于服务真实 application.yml 构造 Binder（占位符按 {@code ${VAR:默认}} 解析）。
     *
     * @return 绑定器
     * @throws IOException yml 读取失败
     */
    private Binder ymlBinder() throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(propertySources::addLast);
        return new Binder(
                ConfigurationPropertySources.from(propertySources),
                new PropertySourcesPlaceholdersResolver(propertySources));
    }

    /**
     * 验证 application.yml 的 {@code apex.todo.*} 块能完整绑定到 TodoProperties。
     *
     * <p>哨兵值被覆盖为 yml 环境变量占位默认值，证明绑定真实发生。
     */
    @Test
    @DisplayName("application.yml 的 apex.todo 块实际绑定 TodoProperties")
    void ymlApexTodoBlockShouldBindTodoProperties() throws IOException {
        TodoProperties bound = sentinelInstance();
        ymlBinder().bind("apex.todo", Bindable.ofInstance(bound));

        assertThat(bound.getSources())
                .containsEntry("workflow", "http://127.0.0.1:8093")
                .containsEntry("office", "http://127.0.0.1:8084");
        assertThat(bound.getPageSize()).isEqualTo(10);
        assertThat(bound.getApprovalLimit()).isEqualTo(10);
        assertThat(bound.getMessageLimit()).isEqualTo(10);
        assertThat(bound.getTimeoutMillis()).isEqualTo(3000L);
        assertThat(bound.getDefaultBulkheadMaxConcurrency()).isEqualTo(8);
    }

    /**
     * 验证旧前缀 {@code aggregation.todo.*} 不再承载配置块（BE-068 前缀漂移回归）。
     *
     * <p>哨兵值在旧前缀下不被覆盖，保持 -1。
     */
    @Test
    @DisplayName("旧前缀 aggregation.todo 不得绑定任何键")
    void legacyAggregationTodoPrefixShouldNotBind() throws IOException {
        TodoProperties bound = sentinelInstance();
        ymlBinder().bind("aggregation.todo", Bindable.ofInstance(bound));

        assertThat(bound.getPageSize()).isEqualTo(-1);
        assertThat(bound.getApprovalLimit()).isEqualTo(-1);
        assertThat(bound.getMessageLimit()).isEqualTo(-1);
        assertThat(bound.getTimeoutMillis()).isEqualTo(-1L);
        assertThat(bound.getDefaultBulkheadMaxConcurrency()).isEqualTo(-1);
        assertThat(bound.getSources()).isEmpty();
    }
}
