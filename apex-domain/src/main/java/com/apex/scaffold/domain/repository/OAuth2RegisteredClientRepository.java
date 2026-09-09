package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2注册客户端仓储接口
 *
 * <p>提供OAuth2客户端的持久化操作，支持增删改查以及按条件查询。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
public interface OAuth2RegisteredClientRepository {
    
    /**
     * 根据客户端ID查找客户端
     *
     * @param clientId 客户端ID
     * @return 客户端实体的Mono包装，不存在则返回empty
     */
    Mono<OAuth2RegisteredClientEntity> findByClientId(String clientId);
    
    /**
     * 根据主键ID查找客户端
     *
     * @param id 主键ID
     * @return 客户端实体的Mono包装，不存在则返回empty
     */
    Mono<OAuth2RegisteredClientEntity> findById(String id);
    
    /**
     * 保存客户端(新增或更新)
     *
     * @param client 客户端实体
     * @return 保存后的客户端实体
     */
    Mono<OAuth2RegisteredClientEntity> save(OAuth2RegisteredClientEntity client);
    
    /**
     * 删除客户端
     *
     * @param id 主键ID
     * @return 空的Mono，表示删除完成
     */
    Mono<Void> deleteById(String id);
    
    /**
     * 分页查询客户端列表
     *
     * @param page 页码(从1开始)
     * @param size 每页大小
     * @return 客户端列表的Flux
     */
    Flux<OAuth2RegisteredClientEntity> findAll(int page, int size);
    
    /**
     * 统计客户端总数
     *
     * @return 客户端总数
     */
    Mono<Long> count();
    
    /**
     * 根据客户端名称模糊查询
     *
     * @param clientName 客户端名称关键字
     * @param page 页码(从1开始)
     * @param size 每页大小
     * @return 客户端列表的Flux
     */
    Flux<OAuth2RegisteredClientEntity> findByClientNameLike(String clientName, int page, int size);
}
