package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.system.SysDatasource;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * 系统数据源配置仓储接口。
 *
 * <p>定义管理后台"数据库配置"模块数据源配置的持久化操作契约，
 * 由基础设施层实现（MyBatis-Plus）。所有阻塞 I/O 实现必须调度到
 * {@code BlockingIoSchedulers.jdbc()} 执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>数据源配置的增删改查（逻辑删除）</li>
 *   <li>按租户隔离查询与分页支持</li>
 *   <li>数据源名称在租户内的唯一性校验查询</li>
 * </ul>
 *
 * <h2>租户隔离约束</h2>
 * <p>所有查询和操作必须强制限定 tenantId，确保：
 * <ul>
 *   <li>每个租户只能访问自己创建的数据源配置</li>
 *   <li>数据源名称在租户内唯一</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <p>{@code password} 字段由应用服务加密后传入仓储，仓储层不感知加密逻辑；
 * 仓储实现不得在日志中输出密码字段。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysDatasource
 */
public interface SysDatasourceRepository {

    /**
     * 根据 ID 查询数据源配置。
     *
     * @param id 数据源 ID
     * @return 数据源实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysDatasource> findById(Long id);

    /**
     * 根据名称和租户 ID 查询数据源配置。
     *
     * <p>用于创建/更新时校验数据源名称在租户内的唯一性。
     *
     * @param name 数据源名称
     * @param tenantId 租户 ID
     * @return 数据源实体（含 {@code Optional} 包装，便于唯一性判断）
     */
    Mono<Optional<SysDatasource>> findByNameAndTenantId(String name, String tenantId);

    /**
     * 根据租户 ID 查询全部数据源配置。
     *
     * <p>按创建时间倒序排列，供分页计算总数与全量导出使用。
     *
     * @param tenantId 租户 ID
     * @return 数据源实体列表
     */
    Mono<List<SysDatasource>> findByTenantId(String tenantId);

    /**
     * 新增数据源配置。
     *
     * <p>主键由 MyBatis-Plus 雪花算法生成，插入成功后回填到返回实体。
     *
     * @param entity 数据源实体（password 必须已加密）
     * @return 含生成 ID 的数据源实体
     */
    Mono<SysDatasource> insert(SysDatasource entity);

    /**
     * 更新数据源配置。
     *
     * <p>基于乐观锁（version 字段）更新，并发冲突时影响行数为 0。
     *
     * @param entity 数据源实体（须携带 ID 与 version）
     * @return 影响行数（0 表示乐观锁冲突或记录不存在）
     */
    Mono<Integer> update(SysDatasource entity);

    /**
     * 删除数据源配置。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。
     *
     * @param id 数据源 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
