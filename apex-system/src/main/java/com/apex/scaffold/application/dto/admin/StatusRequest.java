package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 状态更新请求体（公共 DTO）。
 *
 * <p>用于启用/停用类操作的统一请求体，仅承载 {@code enabled} 布尔值。
 * 历史版本中 {@code DictHandler}、{@code ConfigHandler}、{@code JobHandler}
 * 各自维护了一份同名内部类（逐字重复），已收敛为本公共 DTO，随
 * {@code apex-application} 模块统一分发。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>启用/停用字典类型或字典数据（字典状态更新接口）</li>
 *   <li>启用/停用系统参数（参数状态更新接口）</li>
 *   <li>启用/停用定时任务（任务状态更新接口）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "状态更新请求体（启用/停用）")
public class StatusRequest {

    /** 是否启用：true-启用，false-停用 */
    @Schema(description = "是否启用：true-启用，false-停用", example = "true")
    private boolean enabled;
}
