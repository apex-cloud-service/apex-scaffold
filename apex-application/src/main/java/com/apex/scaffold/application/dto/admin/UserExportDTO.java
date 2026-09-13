package com.apex.scaffold.application.dto.admin;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户导出 DTO（Excel 表头定义）。
 *
 * <p>供 {@code ExcelExportService} 导出用户列表使用，{@code @ExcelProperty}
 * 标注列名与顺序（C5 Excel 导入导出示例）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Schema(description = "用户导出模型")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * UserExportDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class UserExportDTO {

    /** 用户 ID */
    @ExcelProperty("用户ID")
    private Long id;

    /** 登录账号 */
    @ExcelProperty("登录账号")
    private String username;

    /** 昵称 */
    @ExcelProperty("昵称")
    private String nickName;

    /** 手机号 */
    @ExcelProperty("手机号")
    private String phone;

    /** 状态 */
    @ExcelProperty("状态")
    private Integer status;

    /** 最后登录时间 */
    @ExcelProperty("最后登录时间")
    private LocalDateTime lastLoginTime;
}
