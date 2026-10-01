package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.GenConfigPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码生成器生成配置 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 实现生成配置的持久化操作,管理代码生成的参数配置。
 * 继承 {@link BaseMapper} 自动获得常用的增删改查方法。
 *
 * <h3>常用查询场景</h3>
 * <pre>{@code
 * // 查询某个数据源关联的所有配置
 * LambdaQueryWrapper<GenConfigPO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(GenConfigPO::getDatasourceId, datasourceId)
 *        .eq(GenConfigPO::getTenantId, tenantId)
 *        .eq(GenConfigPO::getDeleted, 0)
 *        .orderByDesc(GenConfigPO::getUpdateTime);
 * List<GenConfigPO> configs = mapper.selectList(wrapper);
 *
 * // 按配置名称模糊搜索
 * wrapper.like(GenConfigPO::getConfigName, keyword);
 *
 * // 查询指定模块的配置
 * wrapper.eq(GenConfigPO::getModuleName, "user");
 * }</pre>
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>多对一:GenConfigPO.datasourceId → GenDatasourcePO.id</li>
 *   <li>一对多:GenConfigPO.id ← GenHistoryPO.configId(生成历史记录)</li>
 * </ul>
 *
 * <h3>业务约束</h3>
 * <ul>
 *   <li>删除数据源时,应级联处理关联的生成配置(删除或禁用)</li>
 *   <li>outputPath 字段应在应用层校验,禁止包含路径遍历符号 {@code ..}</li>
 *   <li>tableNames 和 templateIds 字段为 JSON 格式,需在应用层解析</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>所有 Mapper 方法调用必须在 {@code BlockingIoSchedulers.jdbc()} 调度器上执行</li>
 *   <li>查询时自动过滤逻辑删除记录(MyBatis-Plus 逻辑删除插件)</li>
 *   <li>更新时需携带 {@code version} 字段实现乐观锁</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigPO
 * @see GenDatasourcePO
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 */
@Mapper
public interface GenConfigMapper extends BaseMapper<GenConfigPO> {
    // 继承 BaseMapper 提供的所有方法
    // 如需自定义 SQL(如复杂关联查询),可在此添加方法并编写对应的 XML 映射
}
