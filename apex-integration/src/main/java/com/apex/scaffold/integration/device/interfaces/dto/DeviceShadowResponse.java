package com.apex.scaffold.integration.device.interfaces.dto;

import com.apex.scaffold.integration.device.domain.DeviceShadow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DeviceShadowResponse {

    private Long id;

    private String tenantId;

    private String deviceKey;

    /** 期望状态（JSON 节点；原文非法时为 null）。 */
    private JsonNode desired;

    /** 上报状态（JSON 节点；原文非法时为 null）。 */
    private JsonNode reported;

    /** 期望/上报状态的原始 JSON 文本（解析失败时的排障依据）。 */
    private String desiredRaw;

    private String reportedRaw;

    /** 影子版本（乐观锁）。 */
    private Integer version;

    private LocalDateTime createTime;

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
