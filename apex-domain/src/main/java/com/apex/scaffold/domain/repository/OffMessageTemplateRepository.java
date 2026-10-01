package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.OffMessageTemplateEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公消息模板仓储接口
 *
 * <p>定义办公平台消息模板的持久化操作契约，支持消息模板的增删改查、类型查询、启用状态管理等功能。
 * 消息模板用于标准化消息发送，支持占位符替换和多种消息类型。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>消息模板的基础 CRUD 操作</li>
 *   <li>按模板编码查询（唯一标识）</li>
 *   <li>按模板类型查询（通知、提醒、审批等）</li>
 *   <li>查询所有已启用的模板</li>
 *   <li>分页查询和条件过滤</li>
 *   <li>模板启用/禁用状态管理</li>
 * </ul>
 *
 * <h2>查询场景</h2>
 * <ul>
 *   <li>消息发送：根据模板编码获取模板内容</li>
 *   <li>模板管理：分页查询、类型筛选、状态筛选</li>
 *   <li>模板选择器：查询所有可用的消息模板</li>
 * </ul>
 *
 * <h2>技术特性</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>模板编码唯一性：templateCode 作为业务唯一标识</li>
 *   <li>类型分类：支持按消息类型分类管理</li>
 *   <li>启用控制：支持动态启用/禁用模板</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffMessageTemplateEntity
 */
public interface OffMessageTemplateRepository {

    /**
     * 根据模板ID查询模板信息
     *
     * @param id 模板ID
     * @return 模板实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffMessageTemplateEntity> findById(Long id);

    /**
     * 根据模板编码查询模板信息
     *
     * @param templateCode 模板编码，唯一标识
     * @return 模板实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffMessageTemplateEntity> findByTemplateCode(String templateCode);

    /**
     * 根据模板类型查询模板列表
     *
     * @param templateType 模板类型（如通知、提醒、审批）
     * @return 模板列表的Flux流
     */
    Flux<OffMessageTemplateEntity> findByTemplateType(String templateType);

    /**
     * 查询所有已启用的模板
     *
     * @return 已启用模板列表的Flux流
     */
    Flux<OffMessageTemplateEntity> findAllEnabled();

    /**
     * 插入新模板记录
     *
     * @param entity 模板实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OffMessageTemplateEntity entity);

    /**
     * 更新模板信息
     *
     * @param entity 模板实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OffMessageTemplateEntity entity);

    /**
     * 根据ID删除模板（逻辑删除）
     *
     * @param id 模板ID
     * @return 删除操作的Mono包装
     */
    Mono<Void> deleteById(Long id);

    /**
     * 分页查询模板列表
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param templateType 模板类型，可为null表示查询所有类型
     * @param enabled 启用状态，可为null表示查询所有状态
     * @return 分页结果
     */
    Mono<PageResponse<OffMessageTemplateEntity>> findPage(
            Integer pageNum, Integer pageSize, String templateType, Boolean enabled);
}
