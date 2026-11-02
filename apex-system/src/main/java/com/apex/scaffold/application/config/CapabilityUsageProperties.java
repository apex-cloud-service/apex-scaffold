package com.apex.scaffold.application.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 能力调用用量计量配置（方案 D4 计量）。
 *
 * <p>定义能力编码 → 请求 URI 前缀清单的映射，作为
 * {@code GET /api/v2/capability-usage} 的路由聚合口径。
 *
 * <p>口径约束：能力编码与清单须与网关开放接口路由的
 * {@code metadata.open-api.capability} 保持一致（见 apex-gateway 的
 * {@code /open-api/**} 路由组）；前缀匹配的是操作日志中记录的
 * {@code request_uri} 值，网关转发路径变化时需同步调整。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.capability-usage")
public class CapabilityUsageProperties {

    /** 能力编码 → 计量 URI 前缀清单；默认与网关 /open-api/** 四条路由的能力编码对齐。 */
    private Map<String, List<String>> mappings = new LinkedHashMap<>(Map.of(
            "file-storage", List.of("/open-api/file/", "/open-api/files/"),
            "workflow", List.of("/open-api/workflow/"),
            "user-org", List.of("/open-api/users/", "/open-api/user/"),
            "office-message", List.of("/open-api/messages/")));

    /** 单次查询最大时间跨度（天），防止全表范围聚合。 */
    private int maxRangeDays = 92;
}
