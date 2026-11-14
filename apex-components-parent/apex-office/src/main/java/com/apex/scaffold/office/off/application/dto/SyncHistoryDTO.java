package com.apex.scaffold.office.off.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步历史 DTO。
 *
 * <p>组织同步业务域的同步历史记录数据载体，承载同步类型、来源、状态、起止时间、
 * 耗时、部门与员工同步数量及错误信息，用于 {@code OrgSyncService}
 * 同步历史查询用例中实体与服务层之间的数据传输。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "同步历史DTO")
public class SyncHistoryDTO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "同步类型")
    private String syncType;

    @Schema(description = "同步来源")
    private String syncSource;

    @Schema(description = "同步状态")
    private String syncStatus;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "耗时(秒)")
    private Integer duration;

    @Schema(description = "部门数量")
    private Integer deptCount;

    @Schema(description = "员工数量")
    private Integer employeeCount;

    @Schema(description = "错误信息")
    private String errorMessage;
}
