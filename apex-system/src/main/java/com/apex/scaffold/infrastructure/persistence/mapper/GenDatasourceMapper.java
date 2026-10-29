package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.GenDatasourcePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码生成器数据源配置 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 实现数据源配置的持久化操作,提供 CRUD 和条件查询能力。
 * 继承 {@link BaseMapper} 自动获得常用的增删改查方法,无需编写 XML 映射文件。
 *
 * <p><strong>继承的基础方法</strong></p>
 * <ul>
 *   <li>{@code insert(T entity)} - 插入一条记录</li>
 *   <li>{@code deleteById(Serializable id)} - 根据ID删除</li>
 *   <li>{@code updateById(T entity)} - 根据ID更新(需携带version字段实现乐观锁)</li>
 *   <li>{@code selectById(Serializable id)} - 根据ID查询</li>
 *   <li>{@code selectList(Wrapper<T> queryWrapper)} - 条件查询列表</li>
 *   <li>{@code selectPage(Page<T> page, Wrapper<T> queryWrapper)} - 分页查询</li>
 * </ul>
 *
 * <p><strong>常用查询条件示例</strong></p>
 * <pre>{@code
 * // 按租户查询启用的数据源
 * LambdaQueryWrapper<GenDatasourcePO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(GenDatasourcePO::getTenantId, tenantId)
 *        .eq(GenDatasourcePO::getStatus, 1)
 *        .eq(GenDatasourcePO::getDeleted, 0);
 * List<GenDatasourcePO> list = mapper.selectList(wrapper);
 *
 * // 按名称模糊查询
 * wrapper.like(GenDatasourcePO::getName, keyword);
 *
 * // 分页查询
 * Page<GenDatasourcePO> page = new Page<>(current, size);
 * mapper.selectPage(page, wrapper);
 * }</pre>
 *
 * <p><strong>使用约束</strong></p>
 * <ul>
 *   <li>所有数据库操作都是阻塞 I/O,调用时必须在 {@code BlockingIoSchedulers.jdbc()} 调度器上执行</li>
 *   <li>逻辑删除:查询时自动过滤 {@code deleted=1} 的记录(由 MyBatis-Plus 逻辑删除插件处理)</li>
 *   <li>乐观锁:更新时需携带 {@code version} 字段,更新成功后自动递增(由 MyBatis-Plus 乐观锁插件处理)</li>
 *   <li>租户隔离:查询和更新时应显式指定 {@code tenantId} 条件</li>
 * </ul>
 *
 * <p><strong>注意事项</strong></p>
 * <ul>
 *   <li>禁止在 WebFlux 请求处理链中直接调用 Mapper 方法(会阻塞 Netty 线程)</li>
 *   <li>必须通过 {@code Mono.fromCallable(() -> mapper.xxx()).subscribeOn(BlockingIoSchedulers.jdbc())} 包装</li>
 *   <li>复杂查询建议使用 QueryWrapper 构建条件,而非手写 XML</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenDatasourcePO
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 * @see com.apex.scaffold.common.scheduler.BlockingIoSchedulers
 */
@Mapper
public interface GenDatasourceMapper extends BaseMapper<GenDatasourcePO> {
    // 继承 BaseMapper 提供的所有方法
    // 如需自定义 SQL,可在此添加方法并编写对应的 XML 映射
}
