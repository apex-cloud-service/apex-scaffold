package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.BaseEntity;
import com.apex.scaffold.infrastructure.persistence.po.BasePO;
import com.apex.scaffold.infrastructure.persistence.util.ApexLambdaQueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 通用Repository基类实现
 *
 * <p>提供通用的CRUD操作，所有具体Repository实现类可继承此类，
 * 减少重复代码，提高开发效率。
 *
 * <h2>设计目标</h2>
 * <ul>
 *   <li><strong>代码复用</strong>：封装通用CRUD操作，避免重复实现</li>
 *   <li><strong>类型安全</strong>：通过泛型确保类型安全</li>
 *   <li><strong>响应式支持</strong>：所有方法返回Mono，支持响应式编程</li>
 *   <li><strong>分页支持</strong>：集成PageHelper实现分页查询</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>{@code * @Repository
 * public class UserRepositoryImpl extends BaseRepositoryImpl<UserEntity, UserPO>
 *         implements UserRepository {
 *
 *     private final UserMapper userMapper;
 *
 *     public UserRepositoryImpl(UserMapper userMapper, SqlSessionTemplate sqlSessionTemplate) {
 *         super(userMapper, sqlSessionTemplate, UserEntity.class, UserPO.class);
 *         this.userMapper = userMapper;
 *     }
 *
 *     // 自定义查询方法...
 * }
 * }</pre>
 *
 * @param <E> 领域实体类型
 * @param <PO> 持久化对象类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 * @see BasePO
 */
public abstract class BaseRepositoryImpl<E extends BaseEntity, PO extends BasePO> {
    private static final Logger log = LoggerFactory.getLogger(BaseRepositoryImpl.class);

    /** MyBatis Mapper接口 */
    protected final org.apache.ibatis.annotations.Mapper mapper;

    /** SqlSession模板 */
    protected final SqlSessionTemplate sqlSessionTemplate;

    /** 实体类型 */
    protected final Class<E> entityClass;

    /** PO类型 */
    protected final Class<PO> poClass;

    /**
     * 构造函数
     *
     * @param mapper           MyBatis Mapper接口实例
     * @param sqlSessionTemplate SqlSession模板
     * @param entityClass      实体类型
     * @param poClass          PO类型
     */
    protected BaseRepositoryImpl(
            org.apache.ibatis.annotations.Mapper mapper,
            SqlSessionTemplate sqlSessionTemplate,
            Class<E> entityClass,
            Class<PO> poClass) {
        this.mapper = mapper;
        this.sqlSessionTemplate = sqlSessionTemplate;
        this.entityClass = entityClass;
        this.poClass = poClass;
    }

    /**
     * 根据ID查询实体
     *
     * @param id 主键ID
     * @return 实体Mono，不存在时返回空Mono
     */
    public Mono<E> findById(Long id) {
        log.debug("查询实体, type: {}, id: {}", entityClass.getSimpleName(), id);

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        PO po = session.selectOne(entityClass.getSimpleName() + ".selectById", id);
                        if (po == null) {
                            log.debug("实体不存在, type: {}, id: {}", entityClass.getSimpleName(), id);
                            return null;
                        }
                        return convertToEntity(po);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(entity -> {
                    if (entity != null) {
                        log.debug("查询成功, type: {}, id: {}", entityClass.getSimpleName(), id);
                    }
                })
                .doOnError(e -> log.error(
                        "查询失败, type: {}, id: {}, error: {}", entityClass.getSimpleName(), id, e.getMessage(), e));
    }

    /**
     * 保存实体（新增或更新）
     *
     * <p>根据实体ID是否为空判断是新增还是更新：
     * <ul>
     *   <li>ID为空 → 新增记录</li>
     *   <li>ID不为空 → 更新记录</li>
     * </ul>
     *
     * @param entity 实体对象
     * @return 保存后的实体Mono
     */
    public Mono<E> save(E entity) {
        log.debug("保存实体, type: {}", entityClass.getSimpleName());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession(true)) {
                        PO po = convertToPo(entity);

                        if (entity.getId() == null) {
                            // 新增
                            session.insert(entityClass.getSimpleName() + ".insert", po);
                            entity.setId(po.getId());
                            log.debug("新增实体成功, type: {}, id: {}", entityClass.getSimpleName(), po.getId());
                        } else {
                            // 更新
                            session.update(entityClass.getSimpleName() + ".updateById", po);
                            log.debug("更新实体成功, type: {}, id: {}", entityClass.getSimpleName(), entity.getId());
                        }
                        return entity;
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnError(e -> log.error("保存失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 根据ID删除实体（软删除）
     *
     * <p>执行软删除，将deleted字段设置为1。
     *
     * @param id 主键ID
     * @return 删除成功返回true，否则返回false
     */
    public Mono<Boolean> deleteById(Long id) {
        log.debug("删除实体, type: {}, id: {}", entityClass.getSimpleName(), id);

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession(true)) {
                        int updated = session.update(entityClass.getSimpleName() + ".deleteById", id);
                        boolean success = updated > 0;
                        if (success) {
                            log.debug("删除实体成功, type: {}, id: {}", entityClass.getSimpleName(), id);
                        } else {
                            log.debug("实体不存在或已删除, type: {}, id: {}", entityClass.getSimpleName(), id);
                        }
                        return success;
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnError(e -> log.error(
                        "删除失败, type: {}, id: {}, error: {}", entityClass.getSimpleName(), id, e.getMessage(), e));
    }

    /**
     * 查询所有实体
     *
     * @return 实体列表Mono
     */
    public Mono<List<E>> findAll() {
        log.debug("查询所有实体, type: {}", entityClass.getSimpleName());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        List<PO> poList = session.selectList(
                                entityClass.getSimpleName() + ".selectList", new ApexLambdaQueryWrapper<>());
                        return poList.stream().map(this::convertToEntity).toList();
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("查询完成, type: {}, count: {}", entityClass.getSimpleName(), list.size()))
                .doOnError(e -> log.error("查询失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 分页查询
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页数量
     * @return 分页结果Mono
     */
    public Mono<com.apex.scaffold.common.dto.PageResponse<E>> findPage(int pageNum, int pageSize) {
        log.debug("分页查询, type: {}, pageNum: {}, pageSize: {}", entityClass.getSimpleName(), pageNum, pageSize);

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        PageHelper.startPage(pageNum, pageSize);
                        List<PO> list = session.selectList(
                                entityClass.getSimpleName() + ".selectPage", new ApexLambdaQueryWrapper<>());

                        @SuppressWarnings("unchecked")
                        Page<PO> page = list instanceof Page ? (Page<PO>) list : null;
                        long total = page != null ? page.getTotal() : list.size();

                        List<E> records =
                                list.stream().map(this::convertToEntity).toList();

                        return com.apex.scaffold.common.dto.PageResponse.of(records, total, pageNum, pageSize);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(
                        page -> log.debug("分页查询完成, type: {}, total: {}", entityClass.getSimpleName(), page.getTotal()))
                .doOnError(
                        e -> log.error("分页查询失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 根据条件查询单个实体
     *
     * @param wrapper 查询条件包装器
     * @return 实体Mono，不存在时返回空Mono
     */
    public Mono<E> findOne(ApexLambdaQueryWrapper<PO> wrapper) {
        log.debug("条件查询单个实体, type: {}", entityClass.getSimpleName());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        PO po = session.selectOne(entityClass.getSimpleName() + ".selectOne", wrapper);
                        if (po == null) {
                            return null;
                        }
                        return convertToEntity(po);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(entity -> {
                    if (entity != null) {
                        log.debug("条件查询成功, type: {}", entityClass.getSimpleName());
                    } else {
                        log.debug("未找到匹配实体, type: {}", entityClass.getSimpleName());
                    }
                })
                .doOnError(
                        e -> log.error("条件查询失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 根据条件查询实体列表
     *
     * @param wrapper 查询条件包装器
     * @return 实体列表Mono
     */
    public Mono<List<E>> findList(ApexLambdaQueryWrapper<PO> wrapper) {
        log.debug("条件查询列表, type: {}", entityClass.getSimpleName());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        List<PO> poList = session.selectList(entityClass.getSimpleName() + ".selectList", wrapper);
                        return poList.stream().map(this::convertToEntity).toList();
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("条件查询完成, type: {}, count: {}", entityClass.getSimpleName(), list.size()))
                .doOnError(
                        e -> log.error("条件查询失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 检查实体是否存在
     *
     * @param id 主键ID
     * @return 存在返回true，否则返回false
     */
    public Mono<Boolean> existsById(Long id) {
        log.debug("检查实体是否存在, type: {}, id: {}", entityClass.getSimpleName(), id);

        return findById(id)
                .map(entity -> true)
                .defaultIfEmpty(false)
                .doOnSuccess(exists ->
                        log.debug("实体存在检查完成, type: {}, id: {}, exists: {}", entityClass.getSimpleName(), id, exists));
    }

    /**
     * 批量保存实体
     *
     * @param entities 实体列表
     * @return 保存成功的数量
     */
    public Mono<Integer> saveAll(List<E> entities) {
        log.debug("批量保存实体, type: {}, count: {}", entityClass.getSimpleName(), entities.size());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession(true)) {
                        int count = 0;
                        for (E entity : entities) {
                            PO po = convertToPo(entity);
                            if (entity.getId() == null) {
                                session.insert(entityClass.getSimpleName() + ".insert", po);
                                entity.setId(po.getId());
                            } else {
                                session.update(entityClass.getSimpleName() + ".updateById", po);
                            }
                            count++;
                        }
                        log.debug("批量保存完成, type: {}, count: {}", entityClass.getSimpleName(), count);
                        return count;
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnError(
                        e -> log.error("批量保存失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 批量删除实体
     *
     * @param ids ID列表
     * @return 删除成功的数量
     */
    public Mono<Integer> deleteAllById(List<Long> ids) {
        log.debug("批量删除实体, type: {}, count: {}", entityClass.getSimpleName(), ids.size());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession(true)) {
                        int count = 0;
                        for (Long id : ids) {
                            int updated = session.update(entityClass.getSimpleName() + ".deleteById", id);
                            if (updated > 0) {
                                count++;
                            }
                        }
                        log.debug("批量删除完成, type: {}, count: {}", entityClass.getSimpleName(), count);
                        return count;
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnError(
                        e -> log.error("批量删除失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 统计符合条件的实体数量
     *
     * @param wrapper 查询条件包装器
     * @return 实体数量
     */
    public Mono<Long> count(ApexLambdaQueryWrapper<PO> wrapper) {
        log.debug("统计实体数量, type: {}", entityClass.getSimpleName());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        Long count = session.selectOne(entityClass.getSimpleName() + ".selectCount", wrapper);
                        return count != null ? count : 0L;
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(count -> log.debug("统计完成, type: {}, count: {}", entityClass.getSimpleName(), count))
                .doOnError(e -> log.error("统计失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 根据ID批量查询实体
     *
     * @param ids ID列表
     * @return 实体列表Mono
     */
    public Mono<List<E>> findAllById(List<Long> ids) {
        log.debug("批量查询实体, type: {}, count: {}", entityClass.getSimpleName(), ids.size());

        return Mono.fromCallable(() -> {
                    try (SqlSession session =
                            sqlSessionTemplate.getSqlSessionFactory().openSession()) {
                        ApexLambdaQueryWrapper<PO> wrapper = new ApexLambdaQueryWrapper<>();
                        wrapper.in(PO::getId, ids);
                        List<PO> poList = session.selectList(entityClass.getSimpleName() + ".selectList", wrapper);
                        return poList.stream().map(this::convertToEntity).toList();
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("批量查询完成, type: {}, count: {}", entityClass.getSimpleName(), list.size()))
                .doOnError(
                        e -> log.error("批量查询失败, type: {}, error: {}", entityClass.getSimpleName(), e.getMessage(), e));
    }

    /**
     * 将PO转换为Entity
     *
     * <p>子类必须实现此方法，提供PO到Entity的转换逻辑。
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    protected abstract E convertToEntity(PO po);

    /**
     * 将Entity转换为PO
     *
     * <p>子类必须实现此方法，提供Entity到PO的转换逻辑。
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    protected abstract PO convertToPo(E entity);
}
