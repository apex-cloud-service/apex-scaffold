package com.apex.scaffold.application.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前数据库信息 DTO。
 *
 * <p>系统管理-数据库信息查询的响应对象，承载当前数据源的类型、
 * 数据库版本与 JDBC 驱动版本，用于部署环境自检与兼容诊断。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code type}：数据库类型，例如 MySQL、PostgreSQL。</li>
 *   <li>{@code version}：数据库服务端版本号。</li>
 *   <li>{@code driverVersion}：JDBC 驱动版本号。</li>
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
/**n * CurrentDatabaseInfoDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class CurrentDatabaseInfoDTO {

    /**
     * 数据库类型。
     */
    private String type;

    /**
     * 数据库版本。
     */
    private String version;

    /**
     * 驱动版本。
     */
    private String driverVersion;
}
