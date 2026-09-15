package com.apex.scaffold.interfaces.dto.office.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 审批流程分页响应
 *
 * <p>审批流程列表的分页HTTP响应数据。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批流程分页响应")
public class ApprovalProcessPageResponse {
    
    /**
     * 审批流程列表
     */
    @Schema(description = "审批流程列表")
    private List<ApprovalProcessResponse> list;
    
    /**
     * 总记录数
     */
    @Schema(description = "总记录数", example = "100")
    private Long total;
    
    /**
     * 当前页码
     */
    @Schema(description = "当前页码", example = "1")
    private Integer page;
    
    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "20")
    private Integer size;
}
