package com.apex.scaffold.infrastructure.excel;

import com.alibaba.excel.EasyExcel;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Excel 导出服务（C5 Excel 导入导出）。
 *
 * <p>基于 EasyExcel 提供通用列表导出能力：将任意 {@code @ExcelProperty}
 * 标注的 DTO 列表写为 xlsx 字节流，供接口层以附件形式返回。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @ExcelProperty("用户名") private String username;
 * byte[] bytes = excelExportService.export("用户列表", UserExportDTO.class, users);
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Service
public class ExcelExportService {

    /**
     * 导出列表为 xlsx 字节流。
     *
     * @param sheetName 工作表名称
     * @param headClass 表头 DTO 类（字段以 {@code @ExcelProperty} 标注）
     * @param data      导出数据列表
     * @return xlsx 文件字节流
     */
    public byte[] export(String sheetName, Class<?> headClass, List<?> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, headClass).sheet(sheetName).doWrite(data);
        return out.toByteArray();
    }
}
