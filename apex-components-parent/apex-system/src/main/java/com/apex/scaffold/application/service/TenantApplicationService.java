package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.TenantAssembler;
import com.apex.scaffold.application.dto.admin.TenantCreateRequest;
import com.apex.scaffold.application.dto.admin.TenantDTO;
import com.apex.scaffold.application.dto.admin.TenantPageQuery;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.TenantStatusSnapshotDTO;
import com.apex.scaffold.contract.event.TenantChangedEvent;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.exception.TenantLifecycleException;
import com.apex.scaffold.domain.model.exception.TenantQuotaExceededException;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import com.apex.scaffold.domain.repository.TenantRepository;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 租户应用服务。
 *
 * <p>提供租户生命周期管理（创建、启用、停用、冻结、解冻、注销、归档）与配额校验，
 * 是平台管理员管理租户的基础入口。租户表为平台级元数据，不参与租户行级隔离。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>租户编码全局唯一，重名由数据库唯一约束兜底并映射为 {@code TENANT_CODE_EXISTS}</li>
 *   <li>非法状态迁移被拒绝并返回 {@code TENANT_ILLEGAL_TRANSITION}</li>
 *   <li>状态与配额更新支持乐观锁版本校验，影响行数为 0 时返回 {@code TENANT_VERSION_CONFLICT}</li>
 *   <li>配额超限返回 {@code TENANT_QUOTA_EXCEEDED}</li>
 *   <li>停用、冻结、注销、归档状态的租户不得创建新业务数据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantApplicationService {

    /** 受支持的租户状态动作与领域迁移方法映射。 */
    private static final Map<String, Consumer<TenantEntity>> TRANSITIONS = Map.of(
            "disable", TenantEntity::disable,
            "enable", TenantEntity::enable,
            "freeze", TenantEntity::freeze,
            "unfreeze", TenantEntity::unfreeze,
            "deactivate", TenantEntity::deactivate,
            "archive", TenantEntity::archive);

    private final TenantRepository tenantRepository;

    private final com.apex.scaffold.domain.gateway.TenantProvisioningGateway provisioningTransaction;

    private final TenantAssembler tenantAssembler;

    /** 领域事件发布器（D5 主数据分发）：租户创建/状态迁移/配额更新成功后发布变更事件。 */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 查询全部租户。
     *
     * @return 租户 DTO 列表
     */
    public Mono<List<TenantDTO>> listTenants() {
        return tenantRepository
                .findAll()
                .map(tenants -> tenants.stream().map(tenantAssembler::toDTO).toList());
    }

    /**
     * 分页查询租户目录。
     *
     * @param query 分页与筛选条件
     * @return 租户分页结果
     */
    public Mono<PageResponse<TenantDTO>> pageTenants(TenantPageQuery query) {
        return tenantRepository
                .search(
                        query.getTenantCode(),
                        query.getTenantName(),
                        query.getStatus(),
                        query.getPageNum(),
                        query.getPageSize())
                .map(page -> PageResponse.of(
                        page.getList().stream().map(tenantAssembler::toDTO).toList(),
                        page.getTotal(),
                        page.getPageNum(),
                        page.getPageSize()));
    }

    /**
     * 根据租户 ID 查询租户详情。
     *
     * @param tenantId 租户 ID
     * @return 租户 DTO
     * @throws BusinessException 当租户不存在时抛出 {@code TENANT_NOT_FOUND}
     */
    public Mono<TenantDTO> getById(Long tenantId) {
        return requireById(tenantId).map(tenantAssembler::toDTO);
    }

    /**
     * 查询租户认证期状态（统一认证收口 A6）。
     *
     * <p>供认证中心登录前置校验消费：未知租户以 {@code exists=false} 数据化返回
     * （便于消费方缓存负结果），状态机与过期判定与领域模型口径一致。返回类型为
     * {@code apex-api-contract} 契约 DTO，与消费方共用同一类型防漂移（2026-11-07 模块评审 F3）。
     *
     * @param tenantCode 租户编码
     * @return 状态快照；租户不存在时 {@code exists=false}
     */
    public Mono<TenantStatusSnapshotDTO> getTenantStatusByCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return Mono.just(TenantStatusSnapshotDTO.builder().build());
        }
        return getByCode(tenantCode)
                .map(dto -> TenantStatusSnapshotDTO.builder()
                        .exists(true)
                        .status(dto.getStatus())
                        .expired(dto.getExpireTime() != null
                                && dto.getExpireTime().isBefore(java.time.LocalDateTime.now()))
                        .maxUsers(dto.getMaxUsers())
                        .maxStorageMb(dto.getMaxStorageMb())
                        .build())
                .defaultIfEmpty(TenantStatusSnapshotDTO.builder().build());
    }

    /**
     * 根据租户编码查询租户详情。
     *
     * @param tenantCode 租户编码
     * @return 租户 DTO
     * @throws BusinessException 当租户不存在时抛出 {@code TENANT_NOT_FOUND}
     */
    public Mono<TenantDTO> getByCode(String tenantCode) {
        return requireByCode(tenantCode).map(tenantAssembler::toDTO);
    }

    /**
     * 创建租户。
     *
     * <p>先做编码存在性预校验以返回友好提示；并发场景下由数据库唯一约束兜底，
     * 唯一键冲突统一映射为 {@code TENANT_CODE_EXISTS}。新建租户默认为正常状态。</p>
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
            // 方案 B4：提供管理员账号时走开通引导（单事务：部门+管理员+内置角色+授权+回填）；
            // 未提供时保持既有单行创建行为（adminUserId 引用既有账号）。
            if (request.getAdminUsername() != null
                    && !request.getAdminUsername().isBlank()) {
                if (request.getPackageCode() == null || request.getPackageCode().isBlank()) {
                    return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "租户套餐不能为空"));
                }
                if (request.getAdminPassword() == null
                        || request.getAdminPassword().isBlank()) {
                    return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "管理员初始密码不能为空"));
                }
                return provisioningTransaction
                        .provision(new com.apex.scaffold.domain.gateway.TenantProvisioningGateway.ProvisioningRequest(
                                request.getTenantCode(),
                                request.getTenantName(),
                                request.getAdminUsername(),
                                request.getAdminPassword(),
                                request.getPackageCode(),
                                request.getMaxUsers(),
                                request.getMaxStorageMb(),
                                request.getRemark()))
                        .onErrorMap(
                                DuplicateKeyException.class, ex -> new BusinessException(ResultCode.TENANT_CODE_EXISTS))
                        .then(requireByCode(request.getTenantCode()))
                        // D5：开通引导成功后发布租户主数据变更事件；发布失败随链路传播，不吞错误
                        .flatMap(entity -> domainEventPublisher
                                .publish(new TenantChangedEvent(
                                        entity.getTenantCode(), TenantChangedEvent.ACTION_CREATED))
                                .thenReturn(entity))
                        .map(tenantAssembler::toDTO);
            }
            TenantQuota quota = TenantQuota.of(request.getMaxUsers(), request.getMaxStorageMb());
            TenantEntity entity = TenantEntity.create(
                    request.getTenantCode(), request.getTenantName(), request.getAdminUserId(), quota);
            entity.setPackageCode(request.getPackageCode());
            entity.setRemark(request.getRemark());
            return tenantRepository
                    .insert(entity)
                    .onErrorMap(DuplicateKeyException.class, ex -> new BusinessException(ResultCode.TENANT_CODE_EXISTS))
                    // D5：创建成功后发布租户主数据变更事件；发布失败随链路传播，不吞错误
                    .flatMap(rows -> domainEventPublisher
                            .publish(new TenantChangedEvent(entity.getTenantCode(), TenantChangedEvent.ACTION_CREATED))
                            .thenReturn(rows))
                    .map(rows -> {
                        log.info("创建租户成功，code: {}", entity.getTenantCode());
                        return tenantAssembler.toDTO(entity);
                    });
        });
    }

    /**
     * 执行租户状态动作。
     *
     * @param tenantId 租户 ID
     * @param action 动作：disable/enable/freeze/unfreeze/deactivate/archive
     * @param expectedVersion 期望的租户版本号（乐观锁），可为 {@code null}
     * @return 更新后的租户 DTO
     * @throws BusinessException 当动作不支持、租户不存在、状态不允许或版本冲突时抛出
     */
    public Mono<TenantDTO> changeStatus(Long tenantId, String action, Integer expectedVersion) {
        return Mono.defer(() -> {
            Consumer<TenantEntity> transition = TRANSITIONS.get(action);
            if (transition == null) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "不支持的租户状态动作: " + action));
            }
            return requireById(tenantId)
                    .flatMap(entity -> applyTransition(entity, transition, action, expectedVersion));
        });
    }

    /**
     * 更新租户配额。
     *
     * @param tenantId 租户 ID
     * @param maxUsers 最大用户数（null 表示不限制）
     * @param maxStorageMb 最大存储容量 MB（null 表示不限制）
     * @param expectedVersion 期望的租户版本号（乐观锁），可为 {@code null}
     * @return 更新后的租户 DTO
     * @throws BusinessException 当租户不存在或版本冲突时抛出
     */
    public Mono<TenantDTO> updateQuota(Long tenantId, Integer maxUsers, Integer maxStorageMb, Integer expectedVersion) {
        return requireById(tenantId).flatMap(entity -> {
            entity.setQuota(TenantQuota.of(maxUsers, maxStorageMb));
            return persistWithVersion(entity, expectedVersion)
                    // D5：配额更新成功后发布租户主数据变更事件；发布失败随链路传播，不吞错误
                    .flatMap(saved -> domainEventPublisher
                            .publish(new TenantChangedEvent(saved.getTenantCode(), TenantChangedEvent.ACTION_UPDATED))
                            .thenReturn(saved))
                    .map(tenantAssembler::toDTO)
                    .doOnSuccess(dto -> log.info("更新租户配额，code: {}, maxUsers: {}", entity.getTenantCode(), maxUsers));
        });
    }

    /**
     * 断言存储配额（方案批次 B5）。
     *
     * @param tenantCode    租户编码
     * @param currentBytes  当前已用字节数
     * @param incrementBytes 本次新增字节数
     * @throws BusinessException 租户不可写（TENANT_ILLEGAL_TRANSITION）或存储超限（TENANT_QUOTA_EXCEEDED）
     */
    public Mono<Void> assertStorageQuota(String tenantCode, long currentBytes, long incrementBytes) {
        return requireByCode(tenantCode)
                .doOnNext(entity -> {
                    try {
                        entity.assertStorageQuota(currentBytes, incrementBytes);
                    } catch (TenantQuotaExceededException ex) {
                        throw new BusinessException(ResultCode.TENANT_QUOTA_EXCEEDED, ex.getMessage());
                    } catch (TenantLifecycleException ex) {
                        throw new BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION, ex.getMessage());
                    }
                })
                .then();
    }

    /**
     * 校验租户用户数配额增长。
     *
     * <p>供业务模块在创建用户等场景调用，确保租户配额与状态允许增长。
     *
     * @param tenantCode 租户编码
     * @param current 当前用户数
     * @param increment 申请增量
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
     * <p>到期与状态停用分别给出可区分的错误消息：已配置有效期且当前时间已越过
     * 有效期的租户提示续期，状态类拒绝维持既有提示。
     *
     * @param tenantCode 租户编码
     * @throws BusinessException 当租户不存在或不可创建业务数据时抛出
     */
    public Mono<Void> assertCanCreateBusinessData(String tenantCode) {
        return requireByCode(tenantCode).flatMap(entity -> {
            if (!entity.canCreateBusinessData()) {
                return Mono.error(new BusinessException(
                        ResultCode.TENANT_ILLEGAL_TRANSITION,
                        entity.expired() ? "租户已过有效期，续期后才能创建业务数据" : "租户当前状态不允许创建业务数据"));
            }
            return Mono.empty();
        });
    }

    /**
     * 按 ID 加载租户，不存在则抛出 {@code TENANT_NOT_FOUND}。
     *
     * @param tenantId 租户 ID
     * @return 租户实体
     */
    private Mono<TenantEntity> requireById(Long tenantId) {
        return tenantRepository
                .findById(tenantId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.TENANT_NOT_FOUND)));
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
     * @param expectedVersion 期望的租户版本号（乐观锁），可为 {@code null}
     * @return 更新后的租户 DTO
     */
    private Mono<TenantDTO> applyTransition(
            TenantEntity entity, Consumer<TenantEntity> transition, String operationName, Integer expectedVersion) {
        return Mono.defer(() -> {
                    try {
                        transition.accept(entity);
                    } catch (TenantLifecycleException ex) {
                        return Mono.<TenantEntity>error(
                                new BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION, ex.getMessage()));
                    }
                    return persistWithVersion(entity, expectedVersion)
                            // D5：状态迁移成功后发布租户主数据变更事件；发布失败随链路传播，不吞错误
                            .flatMap(saved -> domainEventPublisher
                                    .publish(new TenantChangedEvent(
                                            saved.getTenantCode(), TenantChangedEvent.ACTION_UPDATED))
                                    .thenReturn(saved));
                })
                .map(tenantAssembler::toDTO)
                .doOnSuccess(dto -> log.info("租户状态迁移成功，code: {}, op: {}", entity.getTenantCode(), operationName));
    }

    /**
     * 按期望版本持久化租户，影响行数为 0 时判定为并发冲突。
     *
     * @param entity 租户实体
     * @param expectedVersion 期望的租户版本号（乐观锁），为 {@code null} 时沿用实体当前版本
     * @return 持久化后的租户实体
     */
    private Mono<TenantEntity> persistWithVersion(TenantEntity entity, Integer expectedVersion) {
        if (expectedVersion != null) {
            entity.setLockVersion(expectedVersion);
        }
        return tenantRepository.updateById(entity).flatMap(rows -> {
            if (rows > 0) {
                return Mono.just(entity);
            }
            return Mono.error(new BusinessException(ResultCode.TENANT_VERSION_CONFLICT));
        });
    }
}
