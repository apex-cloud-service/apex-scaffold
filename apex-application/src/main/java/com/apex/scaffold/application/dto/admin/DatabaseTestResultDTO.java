package com.apex.scaffold.application.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库连接测试结果 DTO。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseTestResultDTO {

    /**
     * 是否成功。
     */
    private Boolean success;

    /**
     * 提示信息。
     */
    private String message;

    /**
     * 数据库版本（成功时返回）。
     */
    private String version;
}
