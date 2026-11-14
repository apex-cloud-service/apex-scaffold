package ${infraPackage}.persistence.converter;

import ${domainPackage}.model.entity.${entityName}Entity;
import ${infraPackage}.persistence.po.${entityName}PO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link ${entityName}Entity} 与 {@link ${entityName}PO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ${entityName}PoConverter {

    /**
     * 将${tableComment}实体转换为持久化对象。
     *
     * @param entity ${tableComment}实体
     * @return ${tableComment}持久化对象
     */
    ${entityName}PO toPo(${entityName}Entity entity);

    /**
     * 将${tableComment}持久化对象转换为实体。
     *
     * @param po ${tableComment}持久化对象
     * @return ${tableComment}实体
     */
    ${entityName}Entity toEntity(${entityName}PO po);

    /**
     * 将${tableComment}实体列表转换为持久化对象列表。
     *
     * @param entities ${tableComment}实体列表
     * @return ${tableComment}持久化对象列表
     */
    List<${entityName}PO> toPoList(List<${entityName}Entity> entities);

    /**
     * 将${tableComment}持久化对象列表转换为实体列表。
     *
     * @param pos ${tableComment}持久化对象列表
     * @return ${tableComment}实体列表
     */
    List<${entityName}Entity> toEntityList(List<${entityName}PO> pos);
}
