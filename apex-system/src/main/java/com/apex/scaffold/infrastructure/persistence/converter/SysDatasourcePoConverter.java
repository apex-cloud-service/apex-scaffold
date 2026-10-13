package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.system.SysDatasource;
import com.apex.scaffold.infrastructure.persistence.po.SysDatasourcePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link SysDatasource} 与 {@link SysDatasourcePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成系统数据源配置领域实体
 * 与持久化对象之间的双向映射，忽略未匹配目标属性（BaseEntity/BasePO 的
 * 审计字段由 MyBatis-Plus 自动填充，不参与转换），供 Repository 实现层使用。
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
public interface SysDatasourcePoConverter {

    /**
     * 将系统数据源配置实体转换为持久化对象。
     *
     * @param entity 系统数据源配置实体
     * @return 系统数据源配置持久化对象
     */
    SysDatasourcePO toPo(SysDatasource entity);

    /**
     * 将系统数据源配置持久化对象转换为实体。
     *
     * @param po 系统数据源配置持久化对象
     * @return 系统数据源配置实体
     */
    SysDatasource toEntity(SysDatasourcePO po);

    /**
     * 将系统数据源配置实体列表转换为持久化对象列表。
     *
     * @param entities 系统数据源配置实体列表
     * @return 系统数据源配置持久化对象列表
     */
    List<SysDatasourcePO> toPoList(List<SysDatasource> entities);

    /**
     * 将系统数据源配置持久化对象列表转换为实体列表。
     *
     * @param pos 系统数据源配置持久化对象列表
     * @return 系统数据源配置实体列表
     */
    List<SysDatasource> toEntityList(List<SysDatasourcePO> pos);
}
