package com.apex.scaffold.integration.connector.domain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接器 SPI 加载器（方案 M5 / 批次 E1）。
 *
 * <p>同时支持两种注册方式：ServiceLoader（第三方 JAR 免改库）与 Spring Bean
 * （内置连接器）。内置实现优先：同名类型的 SPI 实现被忽略并记录告警；
 * 内置实现之间重复 {@code type()} 仍以显式失败拒绝，避免隐式覆盖。</p>
 *
 * <p>SPI 发现逐条隔离：单个实现实例化失败、{@code type()} 抛异常或返回空白时跳过并告警；
 * 描述符本身不可继续遍历时停止发现，但保留此前已成功加载的条目。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.1
 */
@Slf4j
public final class ConnectorLoader {

    private ConnectorLoader() {}

    /**
     * 通过 ServiceLoader 发现第三方连接器。
     *
     * <p>使用线程上下文类加载器；上下文类加载器为空时回退到本类加载器。</p>
     *
     * @return 已发现连接器；坏条目被跳过，不抛异常
     */
    public static List<IntegrationConnector> load() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = contextLoader == null ? ConnectorLoader.class.getClassLoader() : contextLoader;
        List<IntegrationConnector> connectors = new ArrayList<>();
        collect(ServiceLoader.load(IntegrationConnector.class, classLoader).iterator(), connectors);
        return List.copyOf(connectors);
    }

    /**
     * 逐条收集 SPI 迭代器中的连接器，隔离单条失败。
     *
     * @param iterator SPI 迭代器
     * @param target   收集目标
     */
    static void collect(Iterator<IntegrationConnector> iterator, List<IntegrationConnector> target) {
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    return;
                }
            } catch (ServiceConfigurationError error) {
                log.warn("连接器 SPI 描述符不可继续遍历，停止发现并保留已加载条目: {}", error.getMessage());
                return;
            }
            IntegrationConnector connector;
            try {
                connector = iterator.next();
            } catch (ServiceConfigurationError | RuntimeException error) {
                log.warn("连接器 SPI 条目实例化失败，已跳过: {}", error.getMessage());
                continue;
            }
            accept(connector, target);
        }
    }

    /**
     * 校验并收集单个 SPI 连接器。
     *
     * @param connector SPI 实现
     * @param target    收集目标
     */
    private static void accept(IntegrationConnector connector, List<IntegrationConnector> target) {
        String type;
        try {
            type = connector.type();
        } catch (RuntimeException error) {
            log.warn("连接器 SPI 实现 type() 调用失败，已跳过: {}", error.getMessage());
            return;
        }
        if (type == null || type.isBlank()) {
            log.warn("连接器 SPI 实现未声明有效 type()，已跳过: {}", connector.getClass().getName());
            return;
        }
        target.add(connector);
        log.info("发现连接器 SPI 实现: type={}, class={}", type, connector.getClass().getName());
    }

    /**
     * 合并内置（Spring Bean）与 SPI 连接器，按类型索引。
     *
     * <p>内置实现优先：SPI 实现与内置同类型时被忽略并记录告警，不抛异常；
     * 内置实现之间重复类型仍抛出 {@link IllegalStateException}。</p>
     *
     * @param builtin 内置连接器，可为 {@code null}
     * @return 类型 → 连接器（不可变）
     * @throws IllegalStateException 内置连接器之间出现重复类型
     */
    public static Map<String, IntegrationConnector> merge(List<IntegrationConnector> builtin) {
        Map<String, IntegrationConnector> merged = new LinkedHashMap<>();
        if (builtin != null) {
            for (IntegrationConnector connector : builtin) {
                put(merged, connector);
            }
        }
        for (IntegrationConnector connector : load()) {
            String type = connector.type();
            IntegrationConnector existing = merged.get(type);
            if (existing != null) {
                log.warn(
                        "连接器类型 {} 已由内置实现 {} 注册，忽略同名 SPI 实现 {}",
                        type,
                        existing.getClass().getName(),
                        connector.getClass().getName());
                continue;
            }
            put(merged, connector);
        }
        return Map.copyOf(merged);
    }

    private static void put(Map<String, IntegrationConnector> merged, IntegrationConnector connector) {
        if (merged.putIfAbsent(connector.type(), connector) != null) {
            throw new IllegalStateException("重复连接器类型: " + connector.type());
        }
    }
}
