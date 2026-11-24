package com.apex.scaffold.domain.gateway;

import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * auth 开放应用注册源端口。
 *
 * <p>system 不依赖 auth 业务库，只通过该端口访问注册源；实现负责内部签名与失败传播。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface OpenAppRegistryGateway {

    /**
     * 创建开放应用。
     *
     * @param request 注册请求
     * @return 注册结果，含本次明文密钥
     */
    Mono<OpenAppRegistrationResponse> create(OpenAppRegistrationRequest request);

    /**
     * 更新开放应用。
     *
     * @param clientId 应用标识
     * @param request 更新请求
     * @return 更新后的注册信息
     */
    Mono<OpenAppRegistrationResponse> update(String clientId, OpenAppRegistrationRequest request);

    /**
     * 查询单个开放应用。
     *
     * @param clientId 应用标识
     * @return 注册信息（不含密钥）
     */
    Mono<OpenAppRegistrationResponse> get(String clientId);

    /**
     * 分页查询开放应用。
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @return 当前页应用列表
     */
    Flux<OpenAppRegistrationResponse> list(int page, int size);

    /**
     * 轮换应用密钥。
     *
     * @param clientId 应用标识
     * @return 注册信息，携带新明文密钥
     */
    Mono<OpenAppRegistrationResponse> rotateSecret(String clientId);

    /**
     * 删除开放应用。
     *
     * @param clientId 应用标识
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> delete(String clientId);

    /**
     * 导入一条旧源开放应用。
     *
     * <p>幂等由 {@code importId} 与源指纹共同保证；同一源行重复导入不会覆盖管理面修改。
     *
     * @param request 导入请求
     * @return 注册信息
     */
    Mono<com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse> importApp(
            com.apex.scaffold.contract.dto.internal.OpenAppImportRequest request);
}
