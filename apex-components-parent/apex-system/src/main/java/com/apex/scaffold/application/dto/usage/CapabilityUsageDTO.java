package com.apex.scaffold.application.dto.usage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 能力调用用量 DTO（方案 D4 计量）。
 *
 * <p>租户 × 能力维度的用量统计行：调用次数为时间段指标（审计日志聚合），
 * 存储用量/设备数/消息量为存量指标（各域内部统计端点），只读输出，不做计费。
 * 存量维度仅在查询指定单个租户时填充，平台全量视角为 {@code null}；
 * 对应数据源不可达时同样为 {@code null}（按源降级）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@Schema(name = "CapabilityUsageDTO", description = "租户 × 能力维度的用量统计行（只读，不做计费）")
public class CapabilityUsageDTO {

    /** 租户标识。 */
    @Schema(description = "租户标识", example = "T001")
    private String tenantId;

    /** 能力编码。 */
    @Schema(description = "能力编码（与网关 open-api.capability 口径一致）", example = "file-storage")
    private String capability;

    /** 统计区间内的调用次数。 */
    @Schema(description = "统计区间内的接口调用次数（审计日志聚合）", example = "128")
    private Long callCount;

    /** 存量指标：租户已用存储字节数（仅 file-storage 能力，单租户查询时填充）。 */
    @Schema(description = "租户已用存储字节数（仅 file-storage 能力，单租户查询时填充；数据源降级为 null）", example = "2048")
    private Long storageUsageBytes;

    /** 存量指标：租户设备总数（仅 iot-device 能力，单租户查询时填充）。 */
    @Schema(description = "租户设备总数（仅 iot-device 能力，单租户查询时填充；数据源降级为 null）", example = "36")
    private Long deviceCount;

    /** 存量指标：租户站内信总量（仅 office-message 能力，单租户查询时填充）。 */
    @Schema(description = "租户站内信总量（仅 office-message 能力，单租户查询时填充；数据源降级为 null）", example = "512")
    private Long messageCount;

    /**
     * 调用次数行构造（存量维度由服务按需填充）。
     *
     * @param tenantId 租户标识
     * @param capability 能力编码
     * @param callCount 调用次数
     */
    public CapabilityUsageDTO(String tenantId, String capability, Long callCount) {
        this.tenantId = tenantId;
        this.capability = capability;
        this.callCount = callCount;
    }
}
