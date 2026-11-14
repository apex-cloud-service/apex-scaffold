package com.apex.scaffold.maskingservice.config;

import com.apex.scaffold.common.masking.JsonFieldMasker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 固定版本策略及 JWT 信任边界配置；不提供请求级规则覆盖。
 * @param issuer 可信签发者
 * @param jwkSetUri 签名公钥地址
 * @param audience 服务受众
 * @param versions 版本策略
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Validated
@ConfigurationProperties("apex.masking")
public record MaskingProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String audience,
        @NotEmpty Map<@NotBlank String, @Valid Policy> versions) {
    /**
     * 校验配置且复制版本映射，防止运行时意外更改。
     */
    public MaskingProperties {
        versions = versions == null ? Map.of() : Map.copyOf(versions);
    }

    /**
     * 固定租户规则。
     * @param tenants 允许的租户标识
     * @param fields 精确字段名规则
     */
    public record Policy(
            @NotEmpty Set<@NotBlank String> tenants, @NotEmpty Map<@NotBlank String, JsonFieldMasker.Rule> fields) {
        /** 复制策略集合，规则构造器会校验自定义保留区间。 */
        public Policy {
            tenants = tenants == null ? Set.of() : Set.copyOf(tenants);
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }
    }
}
