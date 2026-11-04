package com.apex.scaffold.bootall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 组合部署的域定义：每个域对应一份独立子上下文及其关闭开关。
 *
 * <p>保留拆分部署为生产默认；boot-all 仅用于 basic/示例/演示档，且必须显式声明启用的域，
 * 避免默认把全部业务库塞进同一上下文造成数据源、安全链与 Mapper 冲突。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public final class BootAllDomains {

    /** 域定义。 */
    public enum Domain {
        SYSTEM("system", 8082),
        AUTH("auth", 8081),
        WORKFLOW("workflow", 8093),
        FILE("file", 8085),
        OFFICE("office", 8084),
        INTEGRATION("integration", 8094);

        private final String code;
        private final int defaultPort;

        Domain(String code, int defaultPort) {
            this.code = code;
            this.defaultPort = defaultPort;
        }

        public String code() {
            return code;
        }

        public int defaultPort() {
            return defaultPort;
        }

        public static Domain of(String raw) {
            String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (Domain domain : values()) {
                if (domain.name().equals(value) || domain.code.equalsIgnoreCase(raw.trim())) {
                    return domain;
                }
            }
            throw new IllegalArgumentException("未知组合部署域: " + raw);
        }
    }

    private BootAllDomains() {}

    /**
     * 解析待启动域列表。
     *
     * @param configured 配置值（逗号分隔）；空白时返回全部域
     * @return 去重后的域列表
     */
    public static List<Domain> resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of(Domain.values());
        }
        Map<String, Domain> unique = new LinkedHashMap<>();
        for (String item : configured.split(",")) {
            if (!item.isBlank()) {
                Domain domain = Domain.of(item);
                unique.put(domain.code(), domain);
            }
        }
        return List.copyOf(unique.values());
    }

    /**
     * 为指定域生成关闭其他域的属性集合。
     *
     * <p>组件入口开关 {@code apex.component.<domain>.enabled} 默认 true，因此必须显式
     * 关闭未启用的域，否则自动配置会把它们一并装配。
     *
     * @param domain 目标域
     * @return 属性集合
     */
    public static Map<String, Object> propertiesFor(Domain domain) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Domain candidate : Domain.values()) {
            boolean enabled = candidate == domain;
            properties.put("apex.component." + candidate.code() + ".enabled", enabled);
            if (candidate == Domain.INTEGRATION) {
                // 设备子域有独立开关；仅关闭 connector 总开关不足以隔离它。
                properties.put("apex.component.integration.device.enabled", enabled);
            }
        }
        properties.put("spring.application.name", "apex-boot-all-" + domain.code());
        properties.put("server.port", String.valueOf(domain.defaultPort()));
        return properties;
    }
}
