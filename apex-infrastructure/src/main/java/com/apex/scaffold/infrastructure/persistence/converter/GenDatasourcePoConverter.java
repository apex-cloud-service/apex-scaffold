package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import com.apex.scaffold.infrastructure.persistence.po.GenDatasourcePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link GenDatasourceEntity} 与 {@link GenDatasourcePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成代码生成器数据源配置实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * <h2>转换场景</h2>
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
public interface GenDatasourcePoConverter {

    /**
     * 将数据源配置实体转换为持久化对象。
     *
     * @param entity 数据源配置实体
     * @return 数据源配置持久化对象
     */
    GenDatasourcePO toPo(GenDatasourceEntity entity);

    /**
     * 将数据源配置持久化对象转换为实体。
     *
     * @param po 数据源配置持久化对象
     * @return 数据源配置实体
     */
    GenDatasourceEntity toEntity(GenDatasourcePO po);

    /**
     * 将数据源配置实体列表转换为持久化对象列表。
     *
     * @param entities 数据源配置实体列表
     * @return 数据源配置持久化对象列表
     */
    List<GenDatasourcePO> toPoList(List<GenDatasourceEntity> entities);

    /**
     * 将数据源配置持久化对象列表转换为实体列表。
     *
     * @param pos 数据源配置持久化对象列表
     * @return 数据源配置实体列表
     */
    List<GenDatasourceEntity> toEntityList(List<GenDatasourcePO> pos);
}
