package com.apex.scaffold.file.infrastructure.persistence.converter;

import com.apex.scaffold.file.domain.model.entity.DocumentEntity;
import com.apex.scaffold.file.infrastructure.persistence.po.DocumentPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 文档实体与持久化对象转换器。
 *
 * <p>基于 MapStruct 实现文档领域实体（{@link DocumentEntity}）与持久化对象（{@link DocumentPO}）
 * 之间的双向转换。MapStruct 在编译期生成转换代码，性能优于反射，且类型安全。
 *
 * <h2>转换方向</h2>
 * <ul>
 *   <li>{@link #toEntity(DocumentPO)} - PO → Entity：数据库查询结果转为领域实体</li>
 *   <li>{@link #toPo(DocumentEntity)} - Entity → PO：领域实体转为持久化对象</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>查询时：DocumentMapper 返回 DocumentPO → 转换为 DocumentEntity 返回给领域层</li>
 *   <li>保存时：领域层传入 DocumentEntity → 转换为 DocumentPO 存储到数据库</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DocumentEntity
 * @see DocumentPO
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentPoConverter {

    /**
     * 将文档 PO 转换为领域实体。
     *
     * @param po 文档持久化对象
     * @return 文档领域实体
     */
    DocumentEntity toEntity(DocumentPO po);

    /**
     * 将文档领域实体转换为 PO。
     *
     * @param entity 文档领域实体
     * @return 文档持久化对象
     */
    DocumentPO toPo(DocumentEntity entity);
}
