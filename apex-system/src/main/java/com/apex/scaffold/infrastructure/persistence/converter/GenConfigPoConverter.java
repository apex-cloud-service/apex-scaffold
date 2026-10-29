package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.infrastructure.persistence.po.GenConfigPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link GenConfigEntity} 与 {@link GenConfigPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成代码生成器生成配置实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>Repository 查询时：PO → Entity</li>
 *   <li>Repository 写入时：Entity → PO</li>
 *   <li>批量操作时：List&lt;PO&gt; ↔ List&lt;Entity&gt;</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GenConfigPoConverter {

    /**
     * 将生成配置实体转换为持久化对象。
     *
     * @param entity 生成配置实体
     * @return 生成配置持久化对象
     */
    GenConfigPO toPo(GenConfigEntity entity);

    /**
     * 将生成配置持久化对象转换为实体。
     *
     * @param po 生成配置持久化对象
     * @return 生成配置实体
     */
    GenConfigEntity toEntity(GenConfigPO po);

    /**
     * 将生成配置实体列表转换为持久化对象列表。
     *
     * @param entities 生成配置实体列表
     * @return 生成配置持久化对象列表
     */
    List<GenConfigPO> toPoList(List<GenConfigEntity> entities);

    /**
     * 将生成配置持久化对象列表转换为实体列表。
     *
     * @param pos 生成配置持久化对象列表
     * @return 生成配置实体列表
     */
    List<GenConfigEntity> toEntityList(List<GenConfigPO> pos);
}
