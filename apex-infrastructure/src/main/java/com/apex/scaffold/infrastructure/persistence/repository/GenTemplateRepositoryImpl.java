package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenTemplateEntity;
import com.apex.scaffold.domain.repository.GenTemplateRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenTemplatePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenTemplateMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenTemplatePO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link GenTemplateRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责代码生成器模板的持久化读写与类型查询，通过
 * {@link GenTemplatePoConverter} 在领域实体与 {@link GenTemplatePO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>模板的增删改查</li>
 *   <li>按模板类型查询（entity、mapper、service等）</li>
 *   <li>内置模板与自定义模板混合查询</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>模板分类</h2>
 * <ul>
 *   <li><strong>内置模板（isBuiltin=1）</strong>：系统预置，tenantId 通常为 "system"</li>
 *   <li><strong>自定义模板（isBuiltin=0）</strong>：租户创建，使用实际租户ID</li>
 *   <li>查询时需同时返回内置模板和当前租户的自定义模板</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenTemplateRepository
 */
@Repository
@RequiredArgsConstructor
public class GenTemplateRepositoryImpl implements GenTemplateRepository {

    /**
     * 代码模板 Mapper。
     */
    private final GenTemplateMapper templateMapper;

    /**
     * 代码模板实体与持久化对象转换器。
     */
    private final GenTemplatePoConverter templatePoConverter;

    /**
     * 根据 ID 查询模板。
     *
     * @param id 模板 ID
     * @return 模板实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenTemplateEntity> findById(Long id) {
        return Mono.fromCallable(() -> templateMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(templatePoConverter.toEntity(po)));
    }

    /**
     * 根据模板类型查询模板列表。
     *
     * <p>查询指定类型和租户下的所有模板（包括内置模板和自定义模板）。
     * 内置模板的 tenantId 通常为系统保留值（如 "system"）。
     *
     * @param templateType 模板类型（entity、mapper、service等）
     * @param tenantId 租户ID
     * @return 模板实体列表
     */
    @Override
    public Mono<List<GenTemplateEntity>> findByType(String templateType, String tenantId) {
        return Mono.fromCallable(() -> templateMapper.selectList(ConditionUtil.lambda(GenTemplatePO.class)
                        .eq(GenTemplatePO::getTemplateType, templateType)
                        .and(wrapper -> wrapper.eq(GenTemplatePO::getIsBuiltin, 1)
                                .or()
                                .eq(GenTemplatePO::getTenantId, tenantId))
                        .orderByAsc(GenTemplatePO::getIsBuiltin)
                        .orderByDesc(GenTemplatePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(templatePoConverter::toEntityList);
    }

    /**
     * 查询全部模板。
     *
     * <p>查询当前租户下的所有模板（包括内置模板）。
     * 结果按模板类型和创建时间排序。
     *
     * @return 模板实体列表
     */
    @Override
    public Mono<List<GenTemplateEntity>> findAll() {
        return Mono.fromCallable(() -> templateMapper.selectList(ConditionUtil.lambda(GenTemplatePO.class)
                        .orderByAsc(GenTemplatePO::getTemplateType)
                        .orderByDesc(GenTemplatePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(templatePoConverter::toEntityList);
    }

    /**
     * 新增模板。
     *
     * @param entity 模板实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(GenTemplateEntity entity) {
        return Mono.fromCallable(() -> templateMapper.insert(templatePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新模板。
     *
     * @param entity 模板实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(GenTemplateEntity entity) {
        return Mono.fromCallable(() -> templateMapper.updateById(templatePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除模板。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。
     * 内置模板不允许删除，删除前应在应用层拒绝请求。</p>
     *
     * @param id 模板 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> templateMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
