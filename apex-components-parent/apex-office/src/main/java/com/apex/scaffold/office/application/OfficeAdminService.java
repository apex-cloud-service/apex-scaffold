package com.apex.scaffold.office.application;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.port.OfficeUserQueryPort;
import com.apex.scaffold.office.domain.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台管理应用服务
 *
 * <p>提供办公平台应用配置管理和身份绑定审批功能，所有操作均限定在当前租户范围内。
 *
 * <p>功能模块：
 * <ul>
 *   <li>应用配置管理：查询、创建、更新、删除租户的平台应用配置</li>
 *   <li>绑定审批：查询、审批、拒绝、解绑用户与外部平台的身份绑定</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>所有操作必须在租户上下文中执行</li>
 *   <li>跨租户操作被严格拒绝</li>
 *   <li>更新和删除操作使用乐观锁防止并发冲突</li>
 *   <li>审批绑定前校验用户属于同一租户且未删除</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Service
@RequiredArgsConstructor
public class OfficeAdminService {

    private static final String STATUS_REVOKED = "REVOKED";

    private static final String STATUS_PENDING = "PENDING";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private final OfficeRepository repository;
    private final OfficeUserQueryPort users;
    private final OfficeAdminAssembler assembler;

    /**
     * 查询当前租户的所有平台应用配置列表
     *
     * @return 应用配置列表（可能为空列表）
     */
    public Mono<List<OfficePlatformApp>> listApps() {
        return tenant().flatMap(repository::listApps);
    }

    /**
     * 查询指定平台的应用配置
     *
     * @param platform 平台类型
     * @return 应用配置
     * @throws BusinessException 配置不存在时返回 NOT_FOUND
     */
    public Mono<OfficePlatformApp> getApp(OfficePlatform platform) {
        return tenant().flatMap(tenant -> repository
                .findApp(tenant, platform)
                .filter(app -> tenant.equals(app.getTenantId()) && platform == app.getPlatform())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND))));
    }

    /**
     * 创建平台应用配置
     *
     * <p>每个租户-平台组合只能创建一个应用配置，重复创建将失败。
     *
     * @param platform 平台类型
     * @param command 应用配置命令
     * @return 创建后的应用配置
     * @throws BusinessException 应用已存在时返回 PARAM_ERROR
     */
    public Mono<OfficePlatformApp> createApp(OfficePlatform platform, OfficeAppCommand command) {
        return tenant().flatMap(tenant -> repository
                .findApp(tenant, platform)
                .flatMap(existing -> Mono.<OfficePlatformApp>error(new BusinessException(ResultCode.PARAM_ERROR)))
                .switchIfEmpty(
                        Mono.defer(() -> repository.saveApp(assembler.toApp(command, tenant, platform, null, 0)))));
    }

    /**
     * 更新平台应用配置
     *
     * <p>使用乐观锁确保并发安全，版本号不匹配时更新失败。
     *
     * @param platform 平台类型
     * @param command 应用配置命令（必须包含正确的 version）
     * @return 更新后的应用配置
     * @throws BusinessException 配置不存在或版本冲突时
     */
    public Mono<OfficePlatformApp> updateApp(OfficePlatform platform, OfficeAppCommand command) {
        return getApp(platform).flatMap(app -> {
            checkVersion(command.getVersion(), app.getVersion());
            return repository.saveApp(
                    assembler.toApp(command, app.getTenantId(), platform, app.getId(), app.getVersion()));
        });
    }

    /**
     * 逻辑删除平台应用配置
     *
     * @param platform 平台类型
     * @param version 乐观锁版本号
     * @return true-删除成功，false-记录不存在或版本冲突
     * @throws BusinessException 配置不存在时
     */
    public Mono<Boolean> deleteApp(OfficePlatform platform, Integer version) {
        return getApp(platform).flatMap(app -> {
            checkVersion(version, app.getVersion());
            return repository.deleteApp(app.getTenantId(), platform, version);
        });
    }

    /**
     * 分页查询身份绑定列表
     *
     * @param platform 平台类型（为 null 时查询所有平台）
     * @param page 页码（从 1 开始）
     * @param size 每页大小（1-DEFAULT_PAGE_SIZE）
     * @return 绑定列表
     * @throws BusinessException 分页参数无效时返回 PARAM_ERROR
     */
    public Mono<List<OfficeIdentityBinding>> listBindings(OfficePlatform platform, int page, int size) {
        return tenant().flatMap(tenant -> {
            if (page < 1 || size < 1 || size > DEFAULT_PAGE_SIZE) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR));
            }
            return repository.listBindings(tenant, platform, page, size);
        });
    }

    /**
     * 分页查询身份绑定列表，支持状态过滤并返回真实总数。
     *
     * @param platform 平台类型
     * @param status 绑定状态（PENDING/APPROVED/REJECTED/REVOKED），为 null 时不过滤
     * @param page 页码（从 1 开始）
     * @param size 每页大小（1-DEFAULT_PAGE_SIZE）
     * @return 绑定分页结果
     * @throws BusinessException 分页参数无效时返回 PARAM_ERROR
     */
    public Mono<PageResponse<OfficeIdentityBinding>> listBindingsPage(
            OfficePlatform platform, String status, int page, int size) {
        return tenant().flatMap(tenant -> {
            if (page < 1 || size < 1 || size > DEFAULT_PAGE_SIZE) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR));
            }
            return repository.listBindingsPage(tenant, platform, status, page, size);
        });
    }

    /**
     * 审批或拒绝身份绑定
     *
     * <p>只有 PENDING 状态的绑定可以被审批或拒绝。
     * 审批通过前会校验用户是否存在、是否属于同一租户、是否已启用且未删除。
     *
     * @param id 绑定记录 ID
     * @param version 乐观锁版本号
     * @param approved true-批准，false-拒绝
     * @return true-操作成功，false-状态已变更或版本冲突
     * @throws BusinessException 绑定不存在、状态不是 PENDING、用户不存在或跨租户时
     */
    public Mono<Boolean> reviewBinding(Long id, Integer version, boolean approved) {
        return binding(id, version).flatMap(binding -> {
            if (!STATUS_PENDING.equals(binding.getStatus())) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR));
            }
            Mono<Boolean> valid = approved
                    ? users.getById(binding.getUserId())
                            .filter(user -> binding.getUserId().equals(user.getId())
                                    && binding.getTenantId().equals(user.getTenantId())
                                    && user.isEnabled()
                                    && Integer.valueOf(0).equals(user.getDeleted()))
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND)))
                            .thenReturn(true)
                    : Mono.just(true);
            return valid.flatMap(ignored -> repository.changeBindingStatus(
                    binding.getTenantId(), id, version, STATUS_PENDING, approved ? "APPROVED" : "REJECTED"));
        });
    }

    /**
     * 解绑身份绑定
     *
     * <p>将绑定状态修改为 REVOKED，解绑后用户无法再使用该外部身份登录。
     *
     * @param id 绑定记录 ID
     * @param version 乐观锁版本号
     * @return true-解绑成功，false-状态已变更或版本冲突
     * @throws BusinessException 绑定不存在或已经是 REVOKED 状态时
     */
    public Mono<Boolean> unbind(Long id, Integer version) {
        return binding(id, version).flatMap(binding -> {
            if (STATUS_REVOKED.equals(binding.getStatus())) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR));
            }
            return repository.changeBindingStatus(
                    binding.getTenantId(), id, version, binding.getStatus(), STATUS_REVOKED);
        });
    }

    /**
     * 查询绑定记录并校验租户
     *
     * @param id 绑定记录 ID
     * @param version 乐观锁版本号
     * @return 绑定记录
     * @throws BusinessException 绑定不存在或版本冲突时
     */
    private Mono<OfficeIdentityBinding> binding(Long id, Integer version) {
        return tenant().flatMap(tenant -> repository
                        .findBindingById(tenant, id)
                        .filter(binding -> tenant.equals(binding.getTenantId()) && id.equals(binding.getId()))
                        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND))))
                .doOnNext(binding -> checkVersion(version, binding.getVersion()));
    }

    /**
     * 校验乐观锁版本号
     *
     * @param expected 期望的版本号
     * @param actual 实际的版本号
     * @throws BusinessException 版本号为 null、为负数或不匹配时
     */
    private void checkVersion(Integer expected, Integer actual) {
        if (expected == null || expected < 0 || !expected.equals(actual)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    /**
     * 从上下文获取当前租户标识
     *
     * @return 租户标识
     * @throws BusinessException 租户上下文为空时返回 FORBIDDEN
     */
    private Mono<String> tenant() {
        return TenantContext.currentTenantIdReactive()
                .filter(value -> !value.isBlank())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.FORBIDDEN)));
    }
}
