package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.SysSessionConfigPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统会话配置 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 实现会话配置的持久化操作,提供 CRUD 和条件查询能力。
 * 继承 {@link BaseMapper} 自动获得常用的增删改查方法,无需编写 XML 映射文件。
 *
 * <p><strong>继承的基础方法</strong></p>
 * <ul>
 *   <li>{@code insert(T entity)} - 插入一条记录</li>
 *   <li>{@code deleteById(Serializable id)} - 根据 ID 删除</li>
 *   <li>{@code updateById(T entity)} - 根据 ID 更新(需携带 version 字段实现乐观锁)</li>
 *   <li>{@code selectById(Serializable id)} - 根据 ID 查询</li>
 *   <li>{@code selectOne(Wrapper<T> queryWrapper)} - 条件查询单条记录</li>
 *   <li>{@code selectList(Wrapper<T> queryWrapper)} - 条件查询列表</li>
 *   <li>{@code selectPage(Page<T> page, Wrapper<T> queryWrapper)} - 分页查询</li>
 * </ul>
 *
 * <p><strong>常用查询条件示例</strong></p>
 * <pre>{@code
 * // 按租户查询启用的会话配置
 * LambdaQueryWrapper<SysSessionConfigPO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(SysSessionConfigPO::getTenantId, tenantId)
 *        .eq(SysSessionConfigPO::getEnabled, 1)
 *        .eq(SysSessionConfigPO::getDeleted, 0);
 * SysSessionConfigPO config = mapper.selectOne(wrapper);
 *
 * // 查询启用"记住我"功能的配置
 * wrapper.eq(SysSessionConfigPO::getRememberMeEnabled, 1);
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
 *   <li>配置变更立即生效,但不影响已建立的会话</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSessionConfigPO
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 * @see com.apex.scaffold.common.scheduler.BlockingIoSchedulers
 */
@Mapper
public interface SysSessionConfigMapper extends BaseMapper<SysSessionConfigPO> {}
