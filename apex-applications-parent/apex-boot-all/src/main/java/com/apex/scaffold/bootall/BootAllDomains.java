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

    /** 域定义：编码用于配置键与日志，默认端口与独立部署保持一致。 */
    public enum Domain {
        /** 系统管理域，默认端口 8082。 */
        SYSTEM("system", 8082),
        /** 认证中心域，默认端口 8081。 */
        AUTH("auth", 8081),
        /** 工作流域，默认端口 8093。 */
        WORKFLOW("workflow", 8093),
        /** 文件域，默认端口 8085。 */
        FILE("file", 8085),
        /** 办公平台域，默认端口 8084。 */
        OFFICE("office", 8084),
        /** 集成与物联网域，默认端口 8094。 */
        INTEGRATION("integration", 8094);

        private final String code;
        private final int defaultPort;

        /**
         * 绑定域编码与默认端口。
         *
         * @param code 域编码（配置键与日志使用）
         * @param defaultPort 独立部署时的默认业务端口
         */
        Domain(String code, int defaultPort) {
            this.code = code;
            this.defaultPort = defaultPort;
        }

        /**
         * 返回域编码（用于配置键与日志）。
         *
         * @return 小写域编码
         */
        public String code() {
            return code;
        }

        /**
         * 返回域默认业务端口。
         *
         * @return 独立部署默认端口
         */
        public int defaultPort() {
            return defaultPort;
        }

        /**
         * 按名称或编码解析域。
         *
         * @param raw 配置原始值
         * @return 对应域
         */
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
