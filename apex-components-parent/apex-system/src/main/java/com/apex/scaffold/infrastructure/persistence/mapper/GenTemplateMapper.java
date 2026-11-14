package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.GenTemplatePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码生成器模板 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 实现代码生成模板的持久化操作,管理 FreeMarker 模板内容。
 * 继承 {@link BaseMapper} 自动获得常用的增删改查方法。
 *
 * <p><strong>常用查询场景</strong></p>
 * <pre>{@code
 * // 查询指定类型的模板
 * LambdaQueryWrapper<GenTemplatePO> wrapper = new LambdaQueryWrapper<>();
 * wrapper.eq(GenTemplatePO::getTemplateType, "entity")
 *        .eq(GenTemplatePO::getTenantId, tenantId)
 *        .eq(GenTemplatePO::getDeleted, 0)
 *        .orderByAsc(GenTemplatePO::getTemplateType);
 * List<GenTemplatePO> templates = mapper.selectList(wrapper);
 *
 * // 查询系统内置模板和当前租户的自定义模板
 * wrapper.and(w -> w.eq(GenTemplatePO::getIsBuiltin, 1)
 *                   .or()
 *                   .eq(GenTemplatePO::getTenantId, tenantId));
 *
 * // 按模板名称模糊搜索
 * wrapper.like(GenTemplatePO::getTemplateName, keyword);
 * }</pre>
 *
 * <p><strong>模板类型枚举</strong></p>
 * <ul>
 *   <li>entity - 领域实体</li>
 *   <li>po - 持久化对象</li>
 *   <li>mapper - MyBatis Mapper 接口</li>
 *   <li>mapper_xml - MyBatis XML 映射文件</li>
 *   <li>repository - 仓储接口</li>
 *   <li>repository_impl - 仓储实现</li>
 *   <li>service - 应用服务</li>
 *   <li>controller - 控制器</li>
 *   <li>vo/dto/request - 数据对象</li>
 *   <li>converter/assembler - 转换器</li>
 * </ul>
 *
 * <p><strong>业务规则</strong></p>
 * <ul>
 *   <li>内置模板({@code isBuiltin=1})应设为只读,禁止普通用户修改和删除</li>
 *   <li>租户查询时应同时返回系统内置模板({@code isBuiltin=1})和当前租户的自定义模板</li>
 *   <li>templateContent 字段为 LONGTEXT 类型,可存储大型复杂模板</li>
 * </ul>
 *
 * <p><strong>使用约束</strong></p>
 * <ul>
 *   <li>所有 Mapper 方法调用必须在 {@code BlockingIoSchedulers.jdbc()} 调度器上执行</li>
 *   <li>查询时自动过滤逻辑删除记录(MyBatis-Plus 逻辑删除插件)</li>
 *   <li>更新时需携带 {@code version} 字段实现乐观锁</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenTemplatePO
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 */
@Mapper
public interface GenTemplateMapper extends BaseMapper<GenTemplatePO> {
    // 继承 BaseMapper 提供的所有方法
    // 如需自定义 SQL(如全文搜索模板内容),可在此添加方法并编写对应的 XML 映射
}
