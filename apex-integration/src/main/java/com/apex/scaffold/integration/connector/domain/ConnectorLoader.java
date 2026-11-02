package com.apex.scaffold.integration.connector.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 连接器 SPI 加载器（方案 M5 / 批次 E1）。
 *
 * <p>同时支持两种注册方式：ServiceLoader（第三方 JAR 免改库）与 Spring Bean
 * （内置连接器）。重复 {@code type()} 以显式失败拒绝，避免隐式覆盖。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class ConnectorLoader {

    private ConnectorLoader() {}

    /**
     * 通过 ServiceLoader 发现第三方连接器。
     *
     * @return 已发现连接器（加载失败逐条跳过）
     */
    public static List<IntegrationConnector> load() {
        List<IntegrationConnector> connectors = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (IntegrationConnector connector : ServiceLoader.load(
                IntegrationConnector.class,
                classLoader == null ? ConnectorLoader.class.getClassLoader() : classLoader)) {
            try {
                if (connector.type() != null && !connector.type().isBlank()) {
                    connectors.add(connector);
                }
            } catch (RuntimeException ignored) {
                // 单个第三方连接器异常不阻断其它连接器
            }
        }
        return connectors;
    }

    /**
     * 合并内置（Spring Bean）与 SPI 连接器，按类型索引。
     *
     * @param builtin 内置连接器
     * @return 类型 → 连接器（重复类型抛异常）
     */
    public static Map<String, IntegrationConnector> merge(List<IntegrationConnector> builtin) {
        Map<String, IntegrationConnector> merged = new LinkedHashMap<>();
        if (builtin != null) {
            for (IntegrationConnector connector : builtin) {
                put(merged, connector);
            }
        }
        for (IntegrationConnector connector : load()) {
            if (!merged.containsKey(connector.type())) {
                put(merged, connector);
            }
        }
        return Map.copyOf(merged);
    }

    private static void put(Map<String, IntegrationConnector> merged, IntegrationConnector connector) {
        if (merged.putIfAbsent(connector.type(), connector) != null) {
            throw new IllegalStateException("重复连接器类型: " + connector.type());
        }
    }
}
