package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限 DTO。
 *
 * <p>用于接口层返回权限信息，避免领域实体直接暴露。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {

    /**
     * 权限主键 ID。
     */
    @Schema(description = "ID")
    private Long id;

    /**
     * 权限名称。
     *
     * <p>面向管理员的可读名称。</p>
     */
    @Schema(description = "名称")
    private String name;

    /**
     * 权限编码。
     *
     * <p>系统内部唯一标识，如 {@code photo:upload}。</p>
     */
    @Schema(description = "编码")
    private String code;

    /**
     * 权限描述。
     *
     * <p>对权限的补充说明。</p>
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 权限类型。
     *
     * <p>用于分类，如菜单、按钮、接口等。</p>
     */
    @Schema(description = "类型")
    private Integer type;

    /**
     * 权限状态。
     *
     * <p>取值：0-禁用，1-正常。</p>
     */
    @Schema(description = "状态")
    private Integer status;

    /**
     * 创建时间。
     *
     * <p>权限记录首次创建的时间。</p>
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
