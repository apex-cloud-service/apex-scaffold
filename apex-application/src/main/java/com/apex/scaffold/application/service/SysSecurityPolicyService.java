package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.SysSecurityPolicyAssembler;
import com.apex.scaffold.application.dto.admin.SecurityPolicyDTO;
import com.apex.scaffold.application.dto.admin.SecurityPolicyRequest;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.SysSecurityPolicyEntity;
import com.apex.scaffold.domain.repository.SysSecurityPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 租户安全策略应用服务。
 *
 * <p>基于 {@code TenantContext} 当前租户提供安全策略的查询与更新用例：
 * 查询时要求租户上下文已初始化且策略已存在，更新时沿用原记录 ID 与租户归属
 * 并强制保持启用状态，经 {@code SysSecurityPolicyAssembler} 完成对象转换。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSecurityPolicyService {

    private final SysSecurityPolicyRepository securityPolicyRepository;
    private final SysSecurityPolicyAssembler securityPolicyAssembler;

    public Mono<SecurityPolicyDTO> getByTenantId() {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("查询租户安全策略: tenantId={}", tenantId);

        return securityPolicyRepository
                .findByTenantId(tenantId)
                .map(securityPolicyAssembler::toDTO)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "安全策略不存在，请联系管理员初始化配置")));
    }

    public Mono<SecurityPolicyDTO> update(SecurityPolicyRequest request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("更新租户安全策略: tenantId={}", tenantId);

        return securityPolicyRepository
                .findByTenantId(tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "安全策略不存在，请联系管理员初始化配置")))
                .flatMap(existingEntity -> {
                    SysSecurityPolicyEntity updatedEntity = securityPolicyAssembler.toEntity(request);
                    updatedEntity.setId(existingEntity.getId());
                    updatedEntity.setTenantId(tenantId);
                    updatedEntity.setEnabled(1);

                    return securityPolicyRepository.updateById(updatedEntity).then(Mono.just(updatedEntity));
                })
                .map(entity -> {
                    SecurityPolicyDTO dto = securityPolicyAssembler.toDTO(entity);
                    log.info("安全策略更新成功: tenantId={}", tenantId);
                    return dto;
                });
    }
}
