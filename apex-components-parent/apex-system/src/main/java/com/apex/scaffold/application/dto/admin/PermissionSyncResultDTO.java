package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限同步结果响应对象
 *
 * <p>用于返回权限同步操作的结果，包括新增权限、废弃权限的清单。
 * 帮助管理员了解系统中权限定义的变化情况。
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限同步结果响应对象")
public class PermissionSyncResultDTO {

    @Schema(description = "新增的权限代码列表", example = "[\"sys:user:add\", \"sys:role:edit\"]")
    private List<String> addedPermissions;

    @Schema(description = "废弃的权限代码列表", example = "[\"sys:old:delete\"]")
    private List<String> deprecatedPermissions;

    @Schema(description = "未变化的权限代码列表", example = "[\"sys:user:view\", \"sys:role:view\"]")
    private List<String> unchangedPermissions;

    @Schema(description = "新增权限数量", example = "2")
    private Integer addedCount;

    @Schema(description = "废弃权限数量", example = "1")
    private Integer deprecatedCount;

    @Schema(description = "未变化权限数量", example = "10")
    private Integer unchangedCount;

    @Schema(description = "同步时间戳", example = "2026-09-19T10:00:00")
    private String syncTimestamp;
}
