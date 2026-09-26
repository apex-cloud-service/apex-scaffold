package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.SysDatasourcePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统数据源配置 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 实现数据源配置的持久化操作，继承 {@link BaseMapper}
 * 自动获得常用增删改查方法，无需编写 XML 映射文件。
 *
 * <h2>继承的基础方法</h2>
 * <ul>
 *   <li>{@code insert(T entity)} - 插入一条记录（主键由雪花算法自动生成）</li>
 *   <li>{@code deleteById(Serializable id)} - 根据 ID 逻辑删除</li>
 *   <li>{@code updateById(T entity)} - 根据 ID 更新（携带 version 字段实现乐观锁）</li>
 *   <li>{@code selectById(Serializable id)} - 根据 ID 查询</li>
 *   <li>{@code selectList(Wrapper<T> queryWrapper)} - 条件查询列表</li>
 * </ul>
 *
 * <h2>使用约束</h2>
 * <ul>
 *   <li>所有数据库操作都是阻塞 I/O，必须通过
 *       {@code Mono.fromCallable(() -> mapper.xxx()).subscribeOn(BlockingIoSchedulers.jdbc())} 包装</li>
 *   <li>逻辑删除：查询时自动过滤 {@code deleted=1} 的记录（MyBatis-Plus 逻辑删除插件）</li>
 *   <li>乐观锁：更新时需携带 {@code version} 字段（MyBatis-Plus 乐观锁插件）</li>
 *   <li>租户隔离：查询与更新必须显式指定 {@code tenantId} 条件</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysDatasourcePO
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 */
@Mapper
public interface SysDatasourceMapper extends BaseMapper<SysDatasourcePO> {
    // 继承 BaseMapper 提供的所有方法
    // 如需自定义 SQL，可在此添加方法并编写对应的 XML 映射
}
