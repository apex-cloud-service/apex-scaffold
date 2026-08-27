package ${domainPackage}.repository;

import ${domainPackage}.model.entity.${entityName}Entity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * ${tableComment}仓储接口。
 *
 * <p>定义${tableComment}的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ${entityName}Repository {

    /**
     * 根据 ID 查询${tableComment}。
     *
     * @param id ${tableComment} ID
     * @return ${tableComment}实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<${entityName}Entity> findById(Long id);

    /**
     * 查询全部${tableComment}。
     *
     * @return ${tableComment}实体列表
     */
    Mono<List<${entityName}Entity>> findAll();

    /**
     * 新增${tableComment}。
     *
     * @param entity ${tableComment}实体
     * @return 影响行数
     */
    Mono<Integer> insert(${entityName}Entity entity);

    /**
     * 更新${tableComment}。
     *
     * @param entity ${tableComment}实体
     * @return 影响行数
     */
    Mono<Integer> updateById(${entityName}Entity entity);

    /**
     * 删除${tableComment}。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id ${tableComment} ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
