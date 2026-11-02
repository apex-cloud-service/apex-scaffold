package com.apex.scaffold.office.notify.domain;

import com.apex.scaffold.common.dto.PageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 通知模板仓储端口（统一消息门面 / 批次 D1）。
 *
 * <p>所有方法按租户隔离，调用方必须显式传入租户标识；数据库访问在 JDBC 调度器执行。
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 * @see NotifyTemplate
 */
public interface NotifyTemplateRepository {

    /**
     * 按租户与模板编码查询模板（发送与编码唯一性校验共用）。
     *
     * @param tenantId     租户标识
     * @param templateCode 模板编码
     * @return 模板实体，不存在时为空 Mono
     */
    Mono<NotifyTemplate> findByCode(String tenantId, String templateCode);

    /**
     * 按租户与主键查询模板。
     *
     * @param tenantId 租户标识
     * @param id       模板主键
     * @return 模板实体，不存在时为空 Mono
     */
    Mono<NotifyTemplate> findById(String tenantId, Long id);

    /**
     * 插入模板记录并回填主键。
     *
     * @param entity 模板实体
     * @return 插入后的模板实体（含主键）
     */
    Mono<NotifyTemplate> insert(NotifyTemplate entity);

    /**
     * 更新模板记录。
     *
     * @param entity 模板实体，必须包含主键
     * @return 更新后的模板实体
     */
    Mono<NotifyTemplate> update(NotifyTemplate entity);

    /**
     * 逻辑删除模板。
     *
     * @param tenantId 租户标识
     * @param id       模板主键
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(String tenantId, Long id);

    /**
     * 分页查询模板。
     *
     * @param tenantId 租户标识
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @param channel  通道过滤，为 null 表示不限
     * @param keyword  编码或主题模糊匹配，为 null 表示不限
     * @return 分页结果
     */
    Mono<PageResponse<NotifyTemplate>> findPage(
            String tenantId, Integer pageNum, Integer pageSize, String channel, String keyword);

    /**
     * 查询租户内全部启用模板（供运维/管理端下拉展示）。
     *
     * @param tenantId 租户标识
     * @return 启用模板列表
     */
    Flux<NotifyTemplate> findAllEnabled(String tenantId);
}
