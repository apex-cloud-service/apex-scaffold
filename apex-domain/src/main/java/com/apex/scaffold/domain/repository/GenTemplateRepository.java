package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.generator.GenTemplateEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 代码模板仓储接口
 *
 * <p>定义代码生成器模板的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持模板的CRUD操作、按类型查询和内置模板管理。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>模板的增删改查</li>
 *   <li>按模板类型查询（entity、mapper、service等）</li>
 *   <li>内置模板与自定义模板隔离查询</li>
 *   <li>模板内容版本管理</li>
 * </ul>
 *
 * <h2>模板分类</h2>
 * <ul>
 *   <li><strong>内置模板（isBuiltin=1）</strong>：系统预置，不可删除，可能随版本升级更新</li>
 *   <li><strong>自定义模板（isBuiltin=0）</strong>：用户创建，可自由修改和删除</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenTemplateEntity
 */
public interface GenTemplateRepository {

    /**
     * 根据ID查询模板
     *
     * @param id 模板ID
     * @return 模板实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<GenTemplateEntity> findById(Long id);

    /**
     * 根据模板类型查询模板列表
     *
     * <p>查询指定类型和租户下的所有模板（包括内置模板和自定义模板）。
     * 内置模板的 tenantId 通常为系统保留值（如 "system"）。
     *
     * @param templateType 模板类型（entity、mapper、service等）
     * @param tenantId 租户ID
     * @return 模板实体列表
     */
    Mono<List<GenTemplateEntity>> findByType(String templateType, String tenantId);

    /**
     * 查询全部模板
     *
     * <p>查询当前租户下的所有模板（包括内置模板）。
     * 结果按模板类型和创建时间排序。
     *
     * @return 模板实体列表
     */
    Mono<List<GenTemplateEntity>> findAll();

    /**
     * 新增模板
     *
     * <p>插入前应先校验：
     * <ul>
     *   <li>模板内容是否为有效的 FreeMarker 语法</li>
     *   <li>文件后缀是否合法</li>
     *   <li>内置模板不允许普通用户创建</li>
     * </ul>
     *
     * @param entity 模板实体
     * @return 影响行数
     */
    Mono<Integer> insert(GenTemplateEntity entity);

    /**
     * 更新模板
     *
     * <p>更新前应校验：
     * <ul>
     *   <li>内置模板只能由系统管理员修改</li>
     *   <li>模板是否属于当前租户</li>
     * </ul>
     *
     * @param entity 模板实体
     * @return 影响行数
     */
    Mono<Integer> updateById(GenTemplateEntity entity);

    /**
     * 删除模板
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 内置模板不允许删除，删除前应拒绝请求。
     *
     * @param id 模板ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
