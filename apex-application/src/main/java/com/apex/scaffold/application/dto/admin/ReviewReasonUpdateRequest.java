package com.apex.scaffold.application.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新审核原因配置的请求。
 *
 * <p>支持调整展示文案、启用状态、强制备注与排序；期望版本用于乐观锁并发控制。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record ReviewReasonUpdateRequest(
        @NotBlank @Size(max = 128) String label,
        @Size(max = 512) String description,
        boolean enabled,
        boolean requireComment,
        @Min(0) @Max(10000) int sortOrder,
        @Min(0) int expectedVersion) {}
