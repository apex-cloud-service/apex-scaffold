package com.apex.example.dto;

import com.alibaba.excel.annotation.ExcelProperty;
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
public class ExampleUserExportDTO {

    /** 用户 ID（导出列 1）。 */
    @ExcelProperty("用户ID")
    private Long id;

    /** 用户姓名（导出列 2）。 */
    @ExcelProperty("姓名")
    private String username;

    /** 手机号（导出列 3，展示为脱敏格式）。 */
    @ExcelProperty("手机号")
    private String phone;

    /** 用户状态（导出列 4：0=正常 1=禁用）。 */
    @ExcelProperty("状态")
    private Integer status;
}
