package com.apex.scaffold.file.infrastructure.persistence.converter;

import com.apex.scaffold.file.domain.model.entity.FileShareEntity;
import com.apex.scaffold.file.infrastructure.persistence.po.FileSharePO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link FileShareEntity} 与 {@link FileSharePO} 之间的 MapStruct 双向转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FileSharePoConverter {

    /**
     * 将分享实体转换为持久化对象。
     *
     * @param entity 分享实体
     * @return 分享持久化对象
     */
    FileSharePO toPo(FileShareEntity entity);

    /**
     * 将分享持久化对象转换为实体。
     *
     * @param po 分享持久化对象
     * @return 分享实体
     */
    FileShareEntity toEntity(FileSharePO po);
}
