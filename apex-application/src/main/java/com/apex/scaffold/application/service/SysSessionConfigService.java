package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.SysSessionConfigAssembler;
import com.apex.scaffold.application.dto.admin.SessionConfigDTO;
import com.apex.scaffold.application.dto.admin.SessionConfigRequest;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.SysSessionConfigEntity;
import com.apex.scaffold.domain.repository.SysSessionConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 租户会话配置应用服务。
 *
 * <p>基于 {@code TenantContext} 当前租户提供会话配置的查询与更新用例：
 * 查询时要求租户上下文已初始化且配置已存在，更新时沿用原记录 ID 与租户归属
 * 并强制保持启用状态，经 {@code SysSessionConfigAssembler} 完成对象转换。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSessionConfigService {

    private final SysSessionConfigRepository sessionConfigRepository;
    private final SysSessionConfigAssembler sessionConfigAssembler;

    public Mono<SessionConfigDTO> getByTenantId() {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("查询租户会话配置: tenantId={}", tenantId);

        return sessionConfigRepository
                .findByTenantId(tenantId)
                .map(sessionConfigAssembler::toDTO)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话配置不存在，请联系管理员初始化配置")));
    }

    /**
     * 更新当前租户的会话配置。
     *
     * <p>沿用原记录 ID 与租户归属并强制保持启用状态，配置不存在时返回错误信号。</p>
     *
     * @param request 会话配置更新请求
     * @return 更新后的会话配置 DTO
     */
    public Mono<SessionConfigDTO> update(SessionConfigRequest request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("更新租户会话配置: tenantId={}", tenantId);

        return sessionConfigRepository
                .findByTenantId(tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话配置不存在，请联系管理员初始化配置")))
                .flatMap(existingEntity -> {
                    SysSessionConfigEntity updatedEntity = sessionConfigAssembler.toEntity(request);
                    updatedEntity.setId(existingEntity.getId());
                    updatedEntity.setTenantId(tenantId);
                    updatedEntity.setEnabled(1);

                    return sessionConfigRepository.updateById(updatedEntity).then(Mono.just(updatedEntity));
                })
                .map(entity -> {
                    SessionConfigDTO dto = sessionConfigAssembler.toDTO(entity);
                    log.info("会话配置更新成功: tenantId={}", tenantId);
                    return dto;
                });
    }
}
