package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.FileEntity;
import com.apex.scaffold.infrastructure.persistence.po.FilePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link FileEntity} 与 {@link FilePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FilePoConverter {

    /**
     * 将文件实体转换为PO
     *
     * @param entity 文件实体
     * @return 文件PO
     */
    FilePO toPo(FileEntity entity);

    /**
     * 将文件PO转换为实体
     *
     * @param po 文件PO
     * @return 文件实体
     */
    FileEntity toEntity(FilePO po);

    /**
     * 将文件实体列表转换为PO列表
     *
     * @param entities 文件实体列表
     * @return 文件PO列表
     */
    List<FilePO> toPoList(List<FileEntity> entities);

    /**
     * 将文件PO列表转换为实体列表
     *
     * @param pos 文件PO列表
     * @return 文件实体列表
     */
    List<FileEntity> toEntityList(List<FilePO> pos);
}
