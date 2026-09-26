package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库连接测试结果 DTO。
 *
 * <p>系统管理-数据库配置的连接测试响应对象，承载测试是否成功、
 * 提示信息以及成功时返回的数据库版本号。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code success}：连接是否成功，{@code true} 表示成功。</li>
 *   <li>{@code message}：测试结果提示信息，失败时包含错误原因。</li>
 *   <li>{@code version}：数据库版本号，仅在连接成功时返回。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DatabaseTestResultDTO", description = "数据库连接测试结果响应")
public class DatabaseTestResultDTO {

    /**
     * 是否成功。
     */
    @Schema(description = "连接测试是否成功", example = "true")
    private Boolean success;

    /**
     * 提示信息。
     */
    @Schema(description = "测试结果提示信息（失败时包含失败原因）", example = "连接成功")
    private String message;

    /**
     * 数据库版本（成功时返回）。
     */
    @Schema(description = "数据库服务端版本号（仅连接成功时返回）", example = "8.0.36")
    private String version;
}
