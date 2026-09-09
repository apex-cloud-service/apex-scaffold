package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import reactor.core.publisher.Mono;

/**
 * OAuth2授权同意仓储接口
 *
 * <p>提供OAuth2用户授权同意的持久化操作。
 * 用于管理用户对第三方应用的授权记录，支持授权检查、保存和撤销。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>根据客户端ID和用户ID查询授权同意记录</li>
 *   <li>保存或更新用户授权同意</li>
 *   <li>撤销用户对特定客户端的授权</li>
 * </ul>
 *
 * <h2>响应式设计</h2>
 * <p>所有方法返回 {@link Mono} 类型，支持WebFlux响应式编程模型。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
public interface OAuth2AuthorizationConsentRepository {
    
    /**
     * 根据客户端ID和用户ID查询授权同意记录
     *
     * <p>用于检查用户是否已经授权给指定的客户端应用。
     *
     * @param clientId 客户端ID
     * @param userId 用户ID (OpenID)
     * @return 授权同意实体，如果不存在则返回empty
     */
    Mono<OAuth2AuthorizationConsentEntity> findByClientIdAndUserId(String clientId, String userId);
    
    /**
     * 保存或更新授权同意记录
     *
     * <p>如果记录已存在，则更新授权的scope；
     * 如果记录不存在，则创建新的授权同意记录。
     *
     * @param consent 授权同意实体
     * @return 保存后的授权同意实体
     */
    Mono<OAuth2AuthorizationConsentEntity> save(OAuth2AuthorizationConsentEntity consent);
    
    /**
     * 删除授权同意记录
     *
     * <p>用户撤销对某个客户端的授权时调用。
     * 撤销后，用户下次访问该客户端需要重新授权。
     *
     * @param clientId 客户端ID
     * @param userId 用户ID (OpenID)
     * @return 删除操作的完成信号
     */
    Mono<Void> deleteByClientIdAndUserId(String clientId, String userId);
}
