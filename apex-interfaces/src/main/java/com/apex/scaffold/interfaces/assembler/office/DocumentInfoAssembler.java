package com.apex.scaffold.interfaces.assembler.office;

import com.apex.scaffold.domain.office.document.DocumentInfo;
import com.apex.scaffold.interfaces.dto.office.document.DocumentInfoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 文档信息DTO转换器
 *
 * <p>使用MapStruct实现DocumentInfo领域实体与DocumentInfoResponse响应DTO之间的转换。
 * 负责将领域模型转换为客户端可理解的数据传输对象。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DocumentInfoAssembler {

    /**
     * 领域实体转换为响应DTO
     *
     * @param documentInfo 文档信息领域实体
     * @return 文档信息响应DTO
     */
    @Mapping(target = "documentType", source = "documentType", qualifiedByName = "documentTypeToString")
    DocumentInfoResponse toResponse(DocumentInfo documentInfo);

    /**
     * 领域实体列表转换为响应DTO列表
     *
     * @param documentInfos 文档信息领域实体列表
     * @return 文档信息响应DTO列表
     */
    List<DocumentInfoResponse> toResponseList(List<DocumentInfo> documentInfos);

    /**
     * 文档类型枚举转换为字符串
     *
     * @param documentType 文档类型枚举
     * @return 文档类型字符串
     */
    @Named("documentTypeToString")
    default String documentTypeToString(com.apex.scaffold.domain.office.document.DocumentType documentType) {
        return documentType != null ? documentType.name() : null;
    }
}
