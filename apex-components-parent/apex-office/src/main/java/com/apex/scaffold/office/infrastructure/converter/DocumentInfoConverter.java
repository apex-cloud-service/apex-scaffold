package com.apex.scaffold.office.infrastructure.converter;

import com.apex.scaffold.office.domain.document.DocumentInfo;
import com.apex.scaffold.office.domain.document.DocumentType;
import com.apex.scaffold.office.infrastructure.po.DocumentInfoPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 文档信息转换器
 *
 * <p>使用MapStruct实现DocumentInfo领域实体与DocumentInfoPO持久化对象之间的转换。
 * 处理枚举类型、JSON字段等特殊类型的转换逻辑。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings("PMD.LowerCamelCaseVariableNamingRule")
public interface DocumentInfoConverter {

    /**
     * 领域实体转换为持久化对象
     *
     * @param documentInfo 文档信息领域实体
     * @return 文档信息持久化对象
     */
    @Mapping(target = "documentType", source = "documentType", qualifiedByName = "documentTypeToString")
    @Mapping(target = "isFolder", source = "isFolder", defaultValue = "false")
    @Mapping(target = "isArchived", source = "isArchived", defaultValue = "false")
    DocumentInfoPO toPO(DocumentInfo documentInfo);

    /**
     * 持久化对象转换为领域实体
     *
     * @param po 文档信息持久化对象
     * @return 文档信息领域实体
     */
    @Mapping(target = "documentType", source = "documentType", qualifiedByName = "stringToDocumentType")
    @Mapping(target = "isFolder", source = "isFolder", defaultValue = "false")
    @Mapping(target = "isArchived", source = "isArchived", defaultValue = "false")
    DocumentInfo toEntity(DocumentInfoPO po);

    /**
     * 文档类型枚举转换为字符串
     *
     * @param documentType 文档类型枚举
     * @return 文档类型字符串
     */
    @Named("documentTypeToString")
    default String documentTypeToString(DocumentType documentType) {
        return documentType != null ? documentType.name() : null;
    }

    /**
     * 字符串转换为文档类型枚举
     *
     * @param documentType 文档类型字符串
     * @return 文档类型枚举
     */
    @Named("stringToDocumentType")
    default DocumentType stringToDocumentType(String documentType) {
        if (documentType == null) {
            return null;
        }
        try {
            return DocumentType.valueOf(documentType);
        } catch (IllegalArgumentException e) {
            return DocumentType.FILE;
        }
    }
}
