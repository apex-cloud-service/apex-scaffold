package com.apex.scaffold.file.infrastructure.persistence.converter;

import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import com.apex.scaffold.file.infrastructure.persistence.po.FileChunkPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link FileChunkEntity} 与 {@link FileChunkPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FileChunkPoConverter {

    /**
     * 将分片实体转换为PO
     *
     * @param entity 分片实体
     * @return 分片PO
     */
    FileChunkPO toPo(FileChunkEntity entity);

    /**
     * 将分片PO转换为实体
     *
     * @param po 分片PO
     * @return 分片实体
     */
    FileChunkEntity toEntity(FileChunkPO po);

    /**
     * 将分片实体列表转换为PO列表
     *
     * @param entities 分片实体列表
     * @return 分片PO列表
     */
    List<FileChunkPO> toPoList(List<FileChunkEntity> entities);

    /**
     * 将分片PO列表转换为实体列表
     *
     * @param pos 分片PO列表
     * @return 分片实体列表
     */
    List<FileChunkEntity> toEntityList(List<FileChunkPO> pos);
}
