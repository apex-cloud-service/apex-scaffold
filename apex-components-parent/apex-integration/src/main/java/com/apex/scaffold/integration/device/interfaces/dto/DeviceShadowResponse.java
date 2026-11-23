package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceShadow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 设备影子响应（方案 E3 管理端补强）。
 *
 * <p>{@code desired} / {@code reported} 由数据库中的 JSON 文本解析为 JSON 节点返回；
 * 解析失败时以原始文本回退，便于排障。{@code version} 为乐观锁版本，可由调用方
 * 在后续写操作中作为期望版本回传。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Schema(name = "DeviceShadowResponse", description = "设备影子响应；desired/reported 为解析后的 JSON 节点，解析失败时回退原始文本")
public class DeviceShadowResponse {

    @Schema(description = "影子主键 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    @Schema(description = "所属租户 ID", example = "default")
    private String tenantId;

    @Schema(description = "设备标识", example = "dev-0001")
    private String deviceKey;

    /** 期望状态（JSON 节点；原文非法时为 null）。 */
    @Schema(description = "期望状态 JSON 节点；原文非法时为 null", example = "{\"interval\":30}")
    private JsonNode desired;

    /** 上报状态（JSON 节点；原文非法时为 null）。 */
    @Schema(description = "上报状态 JSON 节点；原文非法时为 null", example = "{\"interval\":30,\"battery\":88}")
    private JsonNode reported;

    /** 期望/上报状态的原始 JSON 文本（解析失败时的排障依据）。 */
    @Schema(description = "期望状态的原始 JSON 文本，用于解析失败时排障", example = "{\"interval\":30}")
    private String desiredRaw;

    @Schema(description = "上报状态的原始 JSON 文本，用于解析失败时排障", example = "{\"interval\":30,\"battery\":88}")
    private String reportedRaw;

    /** 影子版本（乐观锁）。 */
    @Schema(description = "影子版本号（乐观锁），写操作可回传作为期望版本", example = "3", type = "integer", format = "int32")
    private Integer version;

    @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime updateTime;

    /**
     * 从影子实体映射。
     *
     * @param shadow 影子实体
     * @param mapper JSON 映射器
     * @return 响应
     */
    public static DeviceShadowResponse from(DeviceShadow shadow, ObjectMapper mapper) {
        DeviceShadowResponse response = new DeviceShadowResponse();
        response.setId(shadow.getId());
        response.setTenantId(shadow.getTenantId());
        response.setDeviceKey(shadow.getDeviceKey());
        response.setDesiredRaw(shadow.getDesired());
        response.setReportedRaw(shadow.getReported());
        response.setDesired(readTree(mapper, shadow.getDesired()));
        response.setReported(readTree(mapper, shadow.getReported()));
        response.setVersion(shadow.getVersion());
        response.setCreateTime(shadow.getCreateTime());
        response.setUpdateTime(shadow.getUpdateTime());
        return response;
    }

    private static JsonNode readTree(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
