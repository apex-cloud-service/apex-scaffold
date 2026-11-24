package com.apex.scaffold.integration.connector.domain.repository;

import com.apex.scaffold.integration.connector.domain.Credential;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 租户级外部凭据仓储端口（方案 M5 / 批次 E2）。
 *
 * <p>支持版本轮换：{@code versionNo} 递增并刷新 {@code rotatedAt}；
 * 密文与 IV 分列存储，仓储层不接触明文。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface CredentialRepository {

    /**
     * 按凭据标识查询（租户限定）。
     *
     * @param tenantId      租户编码
     * @param credentialKey 凭据标识
     * @return 凭据；不存在为空
     */
    Mono<Credential> findByKey(String tenantId, String credentialKey);

    /**
     * 按 ID 查询（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 凭据；跨租户访问视为不存在
     */
    Mono<Credential> findById(String tenantId, Long id);

    /**
     * 查询租户凭据列表（仅元数据语义，密文字段照常返回、由上层裁剪）。
     *
     * @param tenantId      租户编码
     * @param credentialType 凭据类型（可空）
     * @return 凭据列表
     */
    Mono<List<Credential>> list(String tenantId, String credentialType);

    /**
     * 新增凭据（租户内 credentialKey 唯一，初始 versionNo=1）。
     *
     * @param credential 凭据（含密文/IV）
     * @return 落库后的凭据
     */
    Mono<Credential> insert(Credential credential);

    /**
     * 轮换凭据：更新密文与 IV，{@code versionNo} 在当前版本上递增并刷新 {@code rotatedAt}。
     *
     * @param credential 已加载的当前凭据（携带新密文/IV）
     * @return 轮换后的凭据
     */
    Mono<Credential> rotate(Credential credential);

    /**
     * 逻辑删除凭据（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @throws com.apex.scaffold.common.exception.BusinessException 删除 0 行时以 {@code NOT_FOUND} 终止
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> delete(String tenantId, Long id);
}
