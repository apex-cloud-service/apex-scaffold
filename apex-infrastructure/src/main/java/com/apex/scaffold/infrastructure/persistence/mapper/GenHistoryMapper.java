package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.GenHistoryPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码生成历史记录 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 实现生成历史记录的持久化操作,管理代码生成操作的审计日志。
 * 继承 {@link BaseMapper} 自动获得常用的增删改查方法。
 *
 * <h3>常用查询场景</h3>
 * <pre>{@code
 * // 查询某个配置的所有历史记录(分页)
 * LambdaQueryWrapper<GenHistoryPO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(GenHistoryPO::getConfigId, configId)
 *        .eq(GenHistoryPO::getTenantId, tenantId)
 *        .eq(GenHistoryPO::getDeleted, 0)
 *        .orderByDesc(GenHistoryPO::getCreateTime);
 * Page<GenHistoryPO> page = new Page<>(current, size);
 * Page<GenHistoryPO> result = mapper.selectPage(page, wrapper);
 *
 * // 查询失败的生成记录
 * wrapper.eq(GenHistoryPO::getStatus, "FAILED");
 *
 * // 查询某个用户的操作记录
 * wrapper.eq(GenHistoryPO::getOperatorId, userId);
 *
 * // 统计成功率
 * wrapper.eq(GenHistoryPO::getStatus, "SUCCESS");
 * Long successCount = mapper.selectCount(wrapper);
 * }</pre>
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>多对一:GenHistoryPO.configId → GenConfigPO.id</li>
 *   <li>多对一:GenHistoryPO.operatorId → SysUserPO.id</li>
 * </ul>
 *
 * <h3>审计作用</h3>
 * <ul>
 *   <li>追溯代码生成历史,支持回溯和对比</li>
 *   <li>统计生成成功率和失败原因</li>
 *   <li>定位问题配置和模板错误</li>
 *   <li>提供操作审计和责任追踪</li>
 * </ul>
 *
 * <h3>数据保留策略</h3>
 * <ul>
 *   <li>历史记录通常保留而不物理删除,以维护审计完整性</li>
 *   <li>可定期归档或清理过期历史记录(如 90 天前的记录)</li>
 *   <li>即使配置被删除,历史记录也应保留,但 configId 会成为孤儿引用</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>所有 Mapper 方法调用必须在 {@code BlockingIoSchedulers.jdbc()} 调度器上执行</li>
 *   <li>查询时自动过滤逻辑删除记录(MyBatis-Plus 逻辑删除插件)</li>
 *   <li>历史记录创建后通常不允许修改,仅支持查询和删除</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenHistoryPO
 * @see GenConfigPO
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 */
@Mapper
public interface GenHistoryMapper extends BaseMapper<GenHistoryPO> {
    // 继承 BaseMapper 提供的所有方法
    // 如需自定义统计 SQL(如按日期分组统计成功率),可在此添加方法并编写对应的 XML 映射
}
