package com.apex.scaffold.office.infrastructure.spi;

import java.util.Set;

/**
 * 办公平台能力集合值对象。
 *
 * <p>以不可变 {@code Set} 承载办公平台声明的能力标识集合（与平台应用配置中的
 * {@code capabilities} 字段对应），构造时对入参做空集合兜底与防御性拷贝，
 * {@code supports} 方法用于判断平台是否支持指定能力。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
public record OfficePlatformCapabilities(Set<String> capabilities) {
    public OfficePlatformCapabilities {
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }

    /**
     * 判断平台是否支持指定能力。
     *
     * @param capability 能力标识
     * @return 支持返回 true，否则返回 false
     */
    public boolean supports(String capability) {
        return capabilities.contains(capability);
    }
}
