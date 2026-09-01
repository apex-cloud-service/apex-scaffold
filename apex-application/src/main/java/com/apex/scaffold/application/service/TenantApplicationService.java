package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.TenantCreateRequest;
import com.apex.scaffold.application.dto.admin.TenantDTO;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.exception.TenantLifecycleException;
import com.apex.scaffold.domain.model.exception.TenantQuotaExceededException;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import com.apex.scaffold.domain.repository.TenantRepository;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 租户应用服务。
 *
 * <p>提供租户生命周期管理（创建、启用、停用、冻结、解冻、注销、归档）与配额校验，
 * 是平台管理员管理租户的基础入口。租户表为平台级元数据，不参与租户行级隔离。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>租户编码全局唯一，创建时校验（{@code TENANT_CODE_EXISTS}）</li>
 *   <li>非法状态迁移被拒绝并返回 {@code TENANT_ILLEGAL_TRANSITION}</li>
 *   <li>配额超限返回 {@code TENANT_QUOTA_EXCEEDED}</li>
 *   <li>停用、冻结、注销、归档状态的租户不得创建新业务数据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantApplicationService {

    private final TenantRepository tenantRepository;

    /**
     * 查询全部租户。
     *
     * @return 租户 DTO 列表
     */
    public Mono<List<TenantDTO>> listTenants() {
        return tenantRepository
                .findAll()
                .map(tenants -> tenants.stream().map(this::toDTO).toList());
    }

    /**
     * 根据租户编码查询租户详情。
     *
     * @param tenantCode 租户编码
     * @return 租户 DTO
     * @throws BusinessException 当租户不存在时抛出 {@code TENANT_NOT_FOUND}
     */
    public Mono<TenantDTO> getByCode(String tenantCode) {
        return requireByCode(tenantCode).map(this::toDTO);
    }

    /**
     * 创建租户。
     *
     * <p>校验编码唯一性后持久化，新建租户默认为正常状态。</p>
     *
     * @param request 租户创建请求
     * @return 已创建的租户 DTO
     * @throws BusinessException 当编码已存在时抛出 {@code TENANT_CODE_EXISTS}
     */
    public Mono<TenantDTO> createTenant(TenantCreateRequest request) {
        return tenantRepository.existsByCode(request.getTenantCode(), null).flatMap(exists -> {
            if (exists) {
                return Mono.error(new BusinessException(ResultCode.TENANT_CODE_EXISTS));
            }
            TenantQuota quota = TenantQuota.of(request.getMaxUsers(), request.getMaxStorageMb());
            TenantEntity entity = TenantEntity.create(
                    request.getTenantCode(), request.getTenantName(), request.getAdminUserId(), quota);
            entity.setPackageCode(request.getPackageCode());
            entity.setRemark(request.getRemark());
            return tenantRepository.insert(entity).map(rows -> {
                log.info("创建租户成功，code: {}", entity.getTenantCode());
                return toDTO(entity);
            });
        });
    }

    /**
     * 停用租户。
     *
     * @param tenantCode 租户编码
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或状态不允许停用时抛出
     */
    public Mono<TenantDTO> disable(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> applyTransition(entity, TenantEntity::disable, "disable"));
    }

    /**
     * 启用租户。
     *
     * @param tenantCode 租户编码
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或状态不允许启用时抛出
     */
    public Mono<TenantDTO> enable(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> applyTransition(entity, TenantEntity::enable, "enable"));
    }

    /**
     * 冻结租户。
     *
     * @param tenantCode 租户编码
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或状态不允许冻结时抛出
     */
    public Mono<TenantDTO> freeze(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> applyTransition(entity, TenantEntity::freeze, "freeze"));
    }

    /**
     * 解冻租户。
     *
     * @param tenantCode 租户编码
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或状态不允许解冻时抛出
     */
    public Mono<TenantDTO> unfreeze(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> applyTransition(entity, TenantEntity::unfreeze, "unfreeze"));
    }

    /**
     * 注销租户。
     *
     * @param tenantCode 租户编码
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或状态不允许注销时抛出
     */
    public Mono<TenantDTO> deactivate(String tenantCode) {
        return requireByCode(tenantCode)
                .flatMap(entity -> applyTransition(entity, TenantEntity::deactivate, "deactivate"));
    }

    /**
     * 归档租户（终态）。
     *
     * @param tenantCode 租户编码
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或状态不允许归档时抛出
     */
    public Mono<TenantDTO> archive(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> applyTransition(entity, TenantEntity::archive, "archive"));
    }

    /**
     * 更新租户配额。
     *
     * @param tenantCode 租户编码
     * @param maxUsers 最大用户数（null 表示不限制）
     * @param maxStorageMb 最大存储容量 MB（null 表示不限制）
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在时抛出
     */
    public Mono<TenantDTO> updateQuota(String tenantCode, Integer maxUsers, Integer maxStorageMb) {
        return requireByCode(tenantCode).flatMap(entity -> {
            entity.setQuota(TenantQuota.of(maxUsers, maxStorageMb));
            return tenantRepository.updateById(entity).map(rows -> {
                log.info("更新租户配额，code: {}, maxUsers: {}", tenantCode, maxUsers);
                return toDTO(entity);
            });
        });
    }

    /**
     * 校验租户用户数配额增长。
     *
     * <p>供业务模块在创建用户等场景调用，确保租户配额与状态允许增长。</p>
     *
     * @param tenantCode 租户编码
     * @param current 当前用户数
     * @param increment 申请增量
     * @return 完成信号
     * @throws BusinessException 当租户不存在、状态不允许或配额超限时抛出
     */
    public Mono<Void> assertUserQuota(String tenantCode, long current, long increment) {
        return requireByCode(tenantCode)
                .doOnNext(entity -> {
                    try {
                        entity.assertUserQuota(current, increment);
                    } catch (TenantQuotaExceededException ex) {
                        throw new BusinessException(ResultCode.TENANT_QUOTA_EXCEEDED, ex.getMessage());
                    } catch (TenantLifecycleException ex) {
                        throw new BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION, ex.getMessage());
                    }
                })
                .then();
    }

    /**
     * 校验租户是否可创建业务数据。
     *
     * @param tenantCode 租户编码
     * @return 完成信号，不可创建时抛出 {@code TENANT_ILLEGAL_TRANSITION}
     * @throws BusinessException 当租户不存在或不可创建业务数据时抛出
     */
    public Mono<Void> assertCanCreateBusinessData(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> {
            if (!entity.canCreateBusinessData()) {
                return Mono.error(new BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION, "租户当前状态不允许创建业务数据"));
            }
            return Mono.empty();
        });
    }

    /**
     * 按编码加载租户，不存在则抛出 {@code TENANT_NOT_FOUND}。
     *
     * @param tenantCode 租户编码
     * @return 租户实体
     */
    private Mono<TenantEntity> requireByCode(String tenantCode) {
        return tenantRepository
                .findByCode(tenantCode)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.TENANT_NOT_FOUND)));
    }

    /**
     * 应用状态迁移并持久化，捕获领域异常映射为业务错误码。
     *
     * @param entity 租户实体
     * @param transition 状态迁移操作
     * @param operationName 操作名称（用于日志）
     * @return 更新后的租户 DTO
     */
    private Mono<TenantDTO> applyTransition(
            TenantEntity entity, Consumer<TenantEntity> transition, String operationName) {
        return Mono.defer(() -> {
                    try {
                        transition.accept(entity);
                    } catch (TenantLifecycleException ex) {
                        return Mono.<TenantEntity>error(
                                new BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION, ex.getMessage()));
                    }
                    return tenantRepository.updateById(entity).map(rows -> entity);
                })
                .map(this::toDTO)
                .doOnSuccess(e -> log.info("租户状态迁移成功，code: {}, op: {}", entity.getTenantCode(), operationName));
    }

    /**
     * 将租户实体转换为 DTO。
     *
     * @param entity 租户实体
     * @return 租户 DTO
     */
    private TenantDTO toDTO(TenantEntity entity) {
        TenantDTO dto = new TenantDTO();
        dto.setId(entity.getId());
        dto.setTenantCode(entity.getTenantCode());
        dto.setTenantName(entity.getTenantName());
        dto.setAdminUserId(entity.getAdminUserId());
        dto.setPackageCode(entity.getPackageCode());
        dto.setExpireTime(entity.getExpireTime());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        if (entity.getQuota() != null) {
            dto.setMaxUsers(entity.getQuota().getMaxUsers());
            dto.setMaxStorageMb(entity.getQuota().getMaxStorageMb());
        }
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
