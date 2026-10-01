package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限同步结果。
 *
 * <p>用于返回权限同步操作的执行结果,包括新增和删除的权限列表。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(description = "权限同步结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSyncResult {

    @Schema(description = "新增的权限码列表(代码中有但数据库中无)", example = "[\"system:user:add\", \"system:role:edit\"]")
    private List<String> addedPermissions;

    @Schema(description = "废弃的权限码列表(数据库中有但代码中无)", example = "[\"system:old:permission\"]")
    private List<String> removedPermissions;
}
