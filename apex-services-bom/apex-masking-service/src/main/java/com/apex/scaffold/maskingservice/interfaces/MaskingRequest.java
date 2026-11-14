package com.apex.scaffold.maskingservice.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量脱敏输入；不使用自动 toString，避免日志泄漏原文。
 * @param version 配置策略版本
 * @param documents JSON 文档集合
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Schema(description = "按租户授权的固定版本执行批量脱敏；不接受自定义规则")
public record MaskingRequest(
        @NotBlank @Size(max = 64) @Schema(description = "服务配置的规则版本", example = "v1") String version,
        @NotNull @Size(min = 1, max = 100) @Schema(description = "最多一百个 JSON 文档") List<@NotNull JsonNode> documents) {
    /** 返回不含原文的安全描述。 @return 固定描述 */
    @Override
    public String toString() {
        return "MaskingRequest[redacted]";
    }
}
