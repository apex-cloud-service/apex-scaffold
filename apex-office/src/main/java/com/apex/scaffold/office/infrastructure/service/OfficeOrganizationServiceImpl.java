package com.apex.scaffold.office.infrastructure.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.*;
import com.apex.scaffold.office.infrastructure.spi.OfficePlatformOrganizationClient;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台组织服务实现
 *
 * <p>实现 {@link OfficeOrganizationService} 领域端口，聚合各平台的组织客户端实现。
 * 根据平台类型路由到具体的实现类（飞书、钉钉、企业微信）。
 *
 * <p>主要职责：
 * <ul>
 *   <li>路由：根据平台类型选择对应的客户端实现</li>
 *   <li>凭据管理：从 Repository 获取应用配置，从环境变量读取密钥</li>
 *   <li>异常转换：捕获基础设施层异常，转换为领域异常</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeOrganizationServiceImpl implements OfficeOrganizationService {

    private final OfficeRepository officeRepository;
    private final List<OfficePlatformOrganizationClient> platformClients;

    private Map<OfficePlatform, OfficePlatformOrganizationClient> clientMap;

    /**
     * 初始化客户端映射表
     */
    private Map<OfficePlatform, OfficePlatformOrganizationClient> getClientMap() {
        if (clientMap == null) {
            clientMap = platformClients.stream()
                    .collect(Collectors.toMap(OfficePlatformOrganizationClient::platform, Function.identity()));
        }
        return clientMap;
    }

    @Override
    public Mono<OrganizationSyncResult<OfficeDepartment>> fetchDepartments(
            String tenantId, OfficePlatform platform, String cursor) {
        return getAppAndSecret(tenantId, platform)
                .flatMap(appSecret -> {
                    OfficePlatformOrganizationClient client = getClientMap().get(platform);
                    if (client == null) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "不支持的平台类型：" + platform));
                    }
                    return client.fetchDepartments(appSecret.appId(), appSecret.secret(), cursor);
                })
                .onErrorResume(e -> {
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    log.error("拉取部门失败，租户：{}，平台：{}", tenantId, platform, e);
                    return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "拉取部门失败：" + e.getMessage()));
                });
    }

    @Override
    public Mono<OrganizationSyncResult<OfficeMember>> fetchMembers(
            String tenantId, OfficePlatform platform, String cursor) {
        return getAppAndSecret(tenantId, platform)
                .flatMap(appSecret -> {
                    OfficePlatformOrganizationClient client = getClientMap().get(platform);
                    if (client == null) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "不支持的平台类型：" + platform));
                    }
                    return client.fetchMembers(appSecret.appId(), appSecret.secret(), cursor);
                })
                .onErrorResume(e -> {
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    log.error("拉取成员失败，租户：{}，平台：{}", tenantId, platform, e);
                    return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "拉取成员失败：" + e.getMessage()));
                });
    }

    @Override
    public Mono<OrganizationSyncResult<OfficeMember>> fetchDepartmentMembers(
            String tenantId, OfficePlatform platform, String externalDeptId, String cursor) {
        return getAppAndSecret(tenantId, platform)
                .flatMap(appSecret -> {
                    OfficePlatformOrganizationClient client = getClientMap().get(platform);
                    if (client == null) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "不支持的平台类型：" + platform));
                    }
                    return client.fetchDepartmentMembers(appSecret.appId(), appSecret.secret(), externalDeptId, cursor);
                })
                .onErrorResume(e -> {
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    log.error("拉取部门成员失败，租户：{}，平台：，部门：{}", tenantId, platform, externalDeptId, e);
                    return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "拉取部门成员失败：" + e.getMessage()));
                });
    }

    /**
     * 获取应用配置和密钥
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @return 应用标识和密钥
     */
    private Mono<AppSecret> getAppAndSecret(String tenantId, OfficePlatform platform) {
        return officeRepository
                .findApp(tenantId, platform)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "未找到平台应用配置，请先配置应用信息")))
                .flatMap(app -> {
                    if (!app.isEnabled()) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "平台应用未启用"));
                    }

                    String secretRef = app.getSecretReference();
                    if (secretRef == null || secretRef.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "应用密钥引用未配置"));
                    }

                    String secret = System.getenv(secretRef);
                    if (secret == null || secret.isBlank()) {
                        return Mono.error(
                                new BusinessException(ResultCode.BUSINESS_ERROR, "应用密钥未配置，请检查环境变量：" + secretRef));
                    }

                    return Mono.just(new AppSecret(app.getAppId(), secret));
                });
    }

    private record AppSecret(String appId, String secret) {}
}
