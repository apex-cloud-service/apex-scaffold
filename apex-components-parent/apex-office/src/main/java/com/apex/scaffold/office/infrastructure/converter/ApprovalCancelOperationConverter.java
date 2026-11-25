package com.apex.scaffold.office.infrastructure.converter;

import com.apex.scaffold.office.domain.approval.ApprovalCancelOperation;
import com.apex.scaffold.office.domain.approval.ApprovalCancelStatus;
import com.apex.scaffold.office.infrastructure.po.ApprovalCancelOperationPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 审批撤销操作编排状态转换器。
 *
 * <p>使用MapStruct实现领域对象与持久化对象之间的转换，主要处理撤销编排状态枚举与字符串的互转。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings({"PMD.AbstractClassShouldStartWithAbstractNamingRule", "PMD.LowerCamelCaseVariableNamingRule"})
public abstract class ApprovalCancelOperationConverter {

    /**
     * 领域对象转PO
     *
     * @param entity 领域对象
     * @return 持久化对象
     */
    @Mapping(target = "status", source = "status", qualifiedByName = "cancelStatusToString")
    public abstract ApprovalCancelOperationPO toPO(ApprovalCancelOperation entity);

    /**
     * PO转领域对象。
     *
     * @param po 持久化对象
     * @return 领域对象
     */
    @Mapping(target = "status", source = "status", qualifiedByName = "stringToCancelStatus")
    public abstract ApprovalCancelOperation toEntity(ApprovalCancelOperationPO po);

    /**
     * 撤销编排状态枚举转持久化字符串。
     *
     * @param status 撤销编排状态枚举值，允许为 {@code null}
     * @return 枚举名称；入参为 {@code null} 时返回 {@code null}
     */
    @Named("cancelStatusToString")
    protected String cancelStatusToString(ApprovalCancelStatus status) {
        return status != null ? status.name() : null;
    }

    /**
     * 持久化字符串转撤销编排状态枚举。
     *
     * @param status 数据库中存储的枚举名称，允许为 {@code null}
     * @return 对应枚举值；入参为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException 名称不属于 {@link ApprovalCancelStatus} 时抛出
     */
    @Named("stringToCancelStatus")
    protected ApprovalCancelStatus stringToCancelStatus(String status) {
        return status != null ? ApprovalCancelStatus.valueOf(status) : null;
    }
}
