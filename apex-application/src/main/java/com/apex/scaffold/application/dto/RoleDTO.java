package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色 DTO。
 *
 * <p>用于接口层返回角色信息，避免领域实体直接暴露。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class RoleDTO {

    /**
     * 角色主键 ID。
     */
    @Schema(description = "ID")
    private Long id;

    /**
     * 角色名称。
     *
     * <p>面向管理员的可读名称，如"超级管理员"。</p>
     */
    @Schema(description = "名称")
    private String name;

    /**
     * 角色编码。
     *
     * <p>系统内部唯一标识，如 {@code ROLE_ADMIN}。</p>
     */
    @Schema(description = "编码")
    private String code;

    /**
     * 角色描述。
     *
     * <p>对角色的补充说明。</p>
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 角色状态。
     *
     * <p>取值：0-禁用，1-正常。</p>
     */
    @Schema(description = "状态")
    private Integer status;

    /**
     * 创建时间。
     *
     * <p>角色记录首次创建的时间。</p>
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
