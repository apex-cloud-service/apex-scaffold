package com.apex.scaffold.application.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前数据库信息 DTO。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
