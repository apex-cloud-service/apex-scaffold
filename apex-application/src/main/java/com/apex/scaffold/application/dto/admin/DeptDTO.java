package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门 DTO（树形）。
 *
 * <p>管理后台部门管理的展示模型，{@code children} 承载子部门列表
 * （由 {@code DeptService} 按 parentId 树形组装）。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Schema(description = "部门（树形）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * DeptDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DeptDTO {

    /** 部门 ID */
    @Schema(description = "部门ID")
    private Long id;

    /** 父部门 ID */
    @Schema(description = "父部门ID")
    private Long parentId;

    /** 部门名称 */
    @Schema(description = "部门名称")
    private String name;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer orderNum;

    /** 负责人 */
    @Schema(description = "负责人")
    private String leader;

    /** 联系电话 */
    @Schema(description = "联系电话")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 状态（0-停用，1-正常） */
    @Schema(description = "状态（0-停用，1-正常）")
    private Integer status;

    @Schema(description = "乐观锁版本")
    private Integer lockVersion;

    /** 子部门列表 */
    @Schema(description = "子部门列表")
    private List<DeptDTO> children;
}
