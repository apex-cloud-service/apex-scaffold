package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.generator.GenArtifactEntity;
import com.apex.scaffold.infrastructure.persistence.po.GenArtifactPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link GenArtifactEntity} 与 {@link GenArtifactPO} 之间的 MapStruct 双向转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GenArtifactPoConverter {

    /**
     * 将产物实体转换为持久化对象。
     *
     * @param entity 产物实体
     * @return 产物持久化对象
     */
    GenArtifactPO toPo(GenArtifactEntity entity);

    /**
     * 将产物持久化对象转换为实体。
     *
     * @param po 产物持久化对象
     * @return 产物实体
     */
    GenArtifactEntity toEntity(GenArtifactPO po);

    /**
     * 将产物实体列表转换为持久化对象列表。
     *
     * @param entities 产物实体列表
     * @return 产物持久化对象列表
     */
    List<GenArtifactPO> toPoList(List<GenArtifactEntity> entities);

    /**
     * 将产物持久化对象列表转换为实体列表。
     *
     * @param pos 产物持久化对象列表
     * @return 产物实体列表
     */
    List<GenArtifactEntity> toEntityList(List<GenArtifactPO> pos);
}
