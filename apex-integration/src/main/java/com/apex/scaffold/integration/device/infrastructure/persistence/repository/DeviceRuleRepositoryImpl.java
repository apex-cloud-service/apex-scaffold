package com.apex.scaffold.integration.device.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.device.domain.DeviceRule;
import com.apex.scaffold.integration.device.domain.repository.DeviceRuleRepository;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceRuleMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceRulePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link DeviceRuleRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E3）。
 *
 * <p>全部查询显式携带 {@code tenant_id} 条件，叠加租户行级拦截器；
 * 阻塞 MyBatis 访问统一调度到 jdbc 池。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class DeviceRuleRepositoryImpl implements DeviceRuleRepository {

    private final DeviceRuleMapper mapper;

    @Override
    public Mono<List<DeviceRule>> findEnabled(String tenantId) {
        return Mono.fromCallable(() -> mapper
                        .selectList(ConditionUtil.lambda(DeviceRulePO.class)
                                .eq(DeviceRulePO::getTenantId, tenantId)
                                .eq(DeviceRulePO::getEnabled, 1))
                        .stream()
                        .map(this::toEntity)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<DeviceRule>> page(
            String tenantId, String keyword, Boolean enabled, int pageNum, int pageSize) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<DeviceRulePO> wrapper =
                            ConditionUtil.lambda(DeviceRulePO.class).eq(DeviceRulePO::getTenantId, tenantId);
                    if (keyword != null && !keyword.isBlank()) {
                        wrapper.like(DeviceRulePO::getRuleName, keyword.trim());
                    }
                    if (enabled != null) {
                        wrapper.eq(DeviceRulePO::getEnabled, enabled ? 1 : 0);
                    }
                    Long total = mapper.selectCount(wrapper);
                    wrapper.orderByDesc(DeviceRulePO::getId)
                            .last("LIMIT " + safePageSize + " OFFSET " + (long) (safePageNum - 1) * safePageSize);
                    List<DeviceRule> records = mapper.selectList(wrapper).stream()
                            .map(this::toEntity)
                            .toList();
                    return PageResponse.of(records, total == null ? 0L : total, safePageNum, safePageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<DeviceRule> findById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(po -> po != null && tenantId != null && tenantId.equals(po.getTenantId()))
                .map(this::toEntity);
    }

    @Override
    public Mono<DeviceRule> insert(DeviceRule rule) {
        return Mono.fromCallable(() -> {
                    DeviceRulePO po = toPo(rule);
                    if (po.getEnabled() == null) {
                        po.setEnabled(1);
                    }
                    if (po.getDeleted() == null) {
                        po.setDeleted(0);
                    }
                    try {
                        mapper.insert(po);
                    } catch (DuplicateKeyException e) {
                        throw new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.BUSINESS_ERROR,
                                "规则名已存在: " + po.getRuleName());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Boolean> updateEnabled(String tenantId, Long id, boolean enabled) {
        return Mono.fromCallable(() -> {
                    DeviceRulePO patch = new DeviceRulePO();
                    patch.setEnabled(enabled ? 1 : 0);
                    return mapper.update(
                            patch,
                            ConditionUtil.lambda(DeviceRulePO.class)
                                    .eq(DeviceRulePO::getTenantId, tenantId)
                                    .eq(DeviceRulePO::getId, id));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rows -> rows > 0);
    }

    private DeviceRule toEntity(DeviceRulePO po) {
        DeviceRule rule = new DeviceRule();
        rule.setId(po.getId());
        rule.setTenantId(po.getTenantId());
        rule.setRuleName(po.getRuleName());
        rule.setConditionJson(po.getConditionJson());
        rule.setActionType(po.getActionType());
        rule.setActionConfig(po.getActionConfig());
        rule.setEnabled(po.getEnabled());
        rule.setCreateTime(po.getCreateTime());
        rule.setUpdateTime(po.getUpdateTime());
        rule.setDeleted(po.getDeleted());
        return rule;
    }

    private DeviceRulePO toPo(DeviceRule rule) {
        DeviceRulePO po = new DeviceRulePO();
        po.setId(rule.getId());
        po.setTenantId(rule.getTenantId());
        po.setRuleName(rule.getRuleName());
        po.setConditionJson(rule.getConditionJson());
        po.setActionType(rule.getActionType());
        po.setActionConfig(rule.getActionConfig());
        po.setEnabled(rule.getEnabled());
        po.setCreateTime(rule.getCreateTime());
        po.setUpdateTime(rule.getUpdateTime());
        po.setDeleted(rule.getDeleted());
        return po;
    }
}
