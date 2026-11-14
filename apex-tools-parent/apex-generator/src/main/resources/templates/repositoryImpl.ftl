package ${infraPackage}.persistence.repository;

import ${domainPackage}.model.entity.${entityName}Entity;
import ${domainPackage}.repository.${entityName}Repository;
import ${infraPackage}.persistence.converter.${entityName}PoConverter;
import ${infraPackage}.persistence.mapper.${entityName}Mapper;
import ${infraPackage}.persistence.po.${entityName}PO;
import ${infraPackage}.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link ${entityName}Repository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link ${entityName}Entity} 的持久化读写，通过
 * {@link ${entityName}PoConverter} 在领域实体与 {@link ${entityName}PO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 * @see ${entityName}Repository
 */
@Repository
@RequiredArgsConstructor
public class ${entityName}RepositoryImpl implements ${entityName}Repository {

    /**
     * ${tableComment} Mapper。
     */
    private final ${entityName}Mapper ${entityVar}Mapper;

    /**
     * ${tableComment}实体与持久化对象转换器。
     */
    private final ${entityName}PoConverter ${entityVar}PoConverter;

    /**
     * 根据 ID 查询${tableComment}。
     *
     * @param id ${tableComment} ID
     * @return ${tableComment}实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<${entityName}Entity> findById(Long id) {
        return Mono.fromCallable(() -> ${entityVar}Mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(${entityVar}PoConverter.toEntity(po)));
    }

    /**
     * 查询全部${tableComment}。
     *
     * @return ${tableComment}实体列表
     */
    @Override
    public Mono<List<${entityName}Entity>> findAll() {
        return Mono.fromCallable(() -> ${entityVar}Mapper.selectList(
                        ConditionUtil.lambda(${entityName}PO.class).orderByDesc(${entityName}PO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(${entityVar}PoConverter::toEntityList);
    }

    /**
     * 新增${tableComment}。
     *
     * @param entity ${tableComment}实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(${entityName}Entity entity) {
        return Mono.fromCallable(() -> ${entityVar}Mapper.insert(${entityVar}PoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新${tableComment}。
     *
     * @param entity ${tableComment}实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(${entityName}Entity entity) {
        return Mono.fromCallable(() -> ${entityVar}Mapper.updateById(${entityVar}PoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除${tableComment}。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id ${tableComment} ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> ${entityVar}Mapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
