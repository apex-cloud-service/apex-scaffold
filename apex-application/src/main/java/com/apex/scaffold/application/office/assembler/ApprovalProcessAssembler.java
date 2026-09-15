package com.apex.scaffold.application.office.assembler;

import com.apex.scaffold.application.office.command.InitiateApprovalCommand;
import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.interfaces.dto.office.approval.ApprovalProcessResponse;
import com.apex.scaffold.interfaces.dto.office.approval.InitiateApprovalRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 审批流程装配器
 *
 * <p>负责审批流程相关对象之间的转换：
 * <ul>
 *   <li>Request DTO → Command</li>
 *   <li>Command → 领域实体</li>
 *   <li>领域实体 → Response DTO</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApprovalProcessAssembler {
    
    /**
     * Request转Command
     *
     * @param request 请求DTO
     * @return 命令对象
     */
    InitiateApprovalCommand toCommand(InitiateApprovalRequest request);
    
    /**
     * Command转领域实体
     *
     * @param command 命令对象
     * @return 领域实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "platformInstanceId", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "currentNode", ignore = true)
    @Mapping(target = "records", ignore = true)
    @Mapping(target = "resultMessage", ignore = true)
    @Mapping(target = "initiatedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ApprovalProcess toEntity(InitiateApprovalCommand command);
    
    /**
     * 领域实体转Response
     *
     * @param entity 领域实体
     * @return 响应DTO
     */
    @Mapping(source = "status", target = "status")
    ApprovalProcessResponse toResponse(ApprovalProcess entity);
}
