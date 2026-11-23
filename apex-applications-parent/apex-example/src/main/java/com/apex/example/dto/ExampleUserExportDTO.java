package com.apex.example.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 示例用户 Excel 导出表头 DTO
 *
 * <p>演示 EasyExcel 导出能力：字段上的 {@link ExcelProperty} 注解
 * 决定导出列顺序与表头文案。真实业务请按导出场景单独定义，
 * 避免与接口响应 DTO 耦合。</p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ExampleUserExportRow", description = "示例用户 Excel 导出表头；仅用于 xlsx 导出，不作为 HTTP 请求/响应体")
public class ExampleUserExportDTO {

    /** 用户 ID（导出列 1）。 */
    @ExcelProperty("用户ID")
    @Schema(description = "用户 ID（导出列 1）", example = "1", type = "integer", format = "int64")
    private Long id;

    /** 用户姓名（导出列 2）。 */
    @ExcelProperty("姓名")
    @Schema(description = "用户姓名（导出列 2）", example = "张三")
    private String username;

    /** 手机号（导出列 3，展示为脱敏格式）。 */
    @ExcelProperty("手机号")
    @Schema(description = "手机号（导出列 3，已脱敏）", example = "138****8000")
    private String phone;

    /** 用户状态（导出列 4：0=正常 1=禁用）。 */
    @ExcelProperty("状态")
    @Schema(
            description = "用户状态（导出列 4）",
            example = "0",
            allowableValues = {"0", "1"},
            type = "integer",
            format = "int32")
    private Integer status;
}
