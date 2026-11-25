package com.apex.scaffold.application.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * @version 1.3.2
 * @since 1.3.2
 */
@Schema(name = "OverviewResponse", description = "跨域只读聚合门面响应；单源降级不影响其他数据源")
public record OverviewResponse(
        @Schema(description = "租户标识（查询范围）", example = "T001") String tenantId,
        @Schema(description = "聚合生成时间") LocalDateTime generatedAt,
        @Schema(description = "是否存在降级数据源") boolean degraded,
        @Schema(description = "数据源名称 → 单源结果（file/workflow/integration）") Map<String, SourceResult> sources) {

    /**
     * 单数据源结果。
     *
     * @param status {@code UP}（成功）或 {@code DEGRADED}（超时/不可达/解析失败）
     * @param data   下游 {@code Result.data} 原始负载；降级时为 {@code null}
     * @param error  降级原因摘要；成功时为 {@code null}
     */
    @Schema(name = "OverviewSourceResult", description = "单数据源结果")
    public record SourceResult(
            @Schema(description = "状态：UP-成功，DEGRADED-超时/不可达/解析失败", example = "UP") String status,
            @Schema(description = "下游 Result.data 原始负载；降级时为 null") JsonNode data,
            @Schema(description = "降级原因摘要；成功时为 null") String error) {

        /**
         * 构造单数据源成功结果
         *
         * <p>状态固定为 {@code UP}，{@code error} 置为 {@code null}；传入的下游负载原样保留，
         * 由调用方按需解析。
         *
         * @param data 下游 {@code Result.data} 原始负载，允许为 {@code null}
         * @return 状态为 {@code UP} 的单源结果
         */
        public static SourceResult up(JsonNode data) {
            return new SourceResult("UP", data, null);
        }

        /**
         * 构造单数据源降级结果
         *
         * <p>状态固定为 {@code DEGRADED}，负载置为 {@code null}，仅保留降级原因摘要，
         * 使整体 {@code degraded=true} 时仍可区分具体失败来源。
         *
         * @param error 降级原因摘要，允许为 {@code null}
         * @return 状态为 {@code DEGRADED} 的单源结果
         */
        public static SourceResult degraded(String error) {
            return new SourceResult("DEGRADED", null, error);
        }
    }
}
