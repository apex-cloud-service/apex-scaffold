package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import com.apex.scaffold.infrastructure.persistence.po.GenHistoryPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link GenHistoryEntity} 与 {@link GenHistoryPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成代码生成器生成历史实体与持久化对象
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
public interface GenHistoryPoConverter {

    /**
     * 将生成历史实体转换为持久化对象。
     *
     * @param entity 生成历史实体
     * @return 生成历史持久化对象
     */
    GenHistoryPO toPo(GenHistoryEntity entity);

    /**
     * 将生成历史持久化对象转换为实体。
     *
     * @param po 生成历史持久化对象
     * @return 生成历史实体
     */
    GenHistoryEntity toEntity(GenHistoryPO po);

    /**
     * 将生成历史实体列表转换为持久化对象列表。
     *
     * @param entities 生成历史实体列表
     * @return 生成历史持久化对象列表
     */
    List<GenHistoryPO> toPoList(List<GenHistoryEntity> entities);

    /**
     * 将生成历史持久化对象列表转换为实体列表。
     *
     * @param pos 生成历史持久化对象列表
     * @return 生成历史实体列表
     */
    List<GenHistoryEntity> toEntityList(List<GenHistoryPO> pos);
}
