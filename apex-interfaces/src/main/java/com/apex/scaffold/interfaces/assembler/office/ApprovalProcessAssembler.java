package com.apex.scaffold.interfaces.assembler.office;

import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.interfaces.dto.office.approval.ApprovalProcessResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 审批流程DTO组装器
 *
 * <p>使用MapStruct实现领域实体与接口DTO之间的转换，遵循DDD分层原则。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApprovalProcessAssembler {

    /**
     * 领域实体转响应DTO
     *
     * @param entity 领域实体
     * @return 响应DTO
     */
    ApprovalProcessResponse toResponse(ApprovalProcess entity);

    /**
     * 字符串转列表（用于审批人ID转换）
     *
     * @param value 逗号分隔的字符串
     * @return 字符串列表
     */
    default List<String> map(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.split(","));
    }
}
