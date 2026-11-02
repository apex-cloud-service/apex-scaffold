package com.apex.scaffold.application.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 跨域只读聚合门面响应（方案 D5）。
 *
 * <p>{@code GET /internal/v1/overview} 的响应负载：按数据源维度返回统计结果，
 * 单源不可达时该源以 {@code DEGRADED} 呈现（结果缺失 + 错误摘要），整体标记
 * {@code degraded=true}，不影响其他可用数据源。
 *
 * @param tenantId    租户标识（查询范围）
 * @param generatedAt 聚合生成时间
 * @param degraded    是否存在降级数据源
 * @param sources     数据源名称 → 单源结果（file/workflow/integration）
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
public record OverviewResponse(
        String tenantId, LocalDateTime generatedAt, boolean degraded, Map<String, SourceResult> sources) {

    /**
     * 单数据源结果。
     *
     * @param status {@code UP}（成功）或 {@code DEGRADED}（超时/不可达/解析失败）
     * @param data   下游 {@code Result.data} 原始负载；降级时为 {@code null}
     * @param error  降级原因摘要；成功时为 {@code null}
     */
    public record SourceResult(String status, JsonNode data, String error) {

        /** 单源成功结果。 */
        public static SourceResult up(JsonNode data) {
            return new SourceResult("UP", data, null);
        }

        /** 单源降级结果。 */
        public static SourceResult degraded(String error) {
            return new SourceResult("DEGRADED", null, error);
        }
    }
}
