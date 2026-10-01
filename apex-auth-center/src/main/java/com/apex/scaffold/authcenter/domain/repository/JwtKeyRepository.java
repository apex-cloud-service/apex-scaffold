package com.apex.scaffold.authcenter.domain.repository;

import com.apex.scaffold.authcenter.domain.model.entity.JwtKeyEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT密钥仓储接口
 *
 * @author Apex Team
 * @since 2026-09-25
 */
public interface JwtKeyRepository {

    /**
     * 保存JWT密钥
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param jwtKey TODO
     * @return TODO
     */
    Mono<JwtKeyEntity> save(JwtKeyEntity jwtKey);

    /**
     * 根据ID查询
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param id TODO
     * @return TODO
     */
    Mono<JwtKeyEntity> findById(Long id);

    /**
     * 根据版本号查询
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param keyVersion TODO
     * @return TODO
     */
    Mono<JwtKeyEntity> findByKeyVersion(String keyVersion);

    /**
     * 查询当前活跃的密钥
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    Mono<JwtKeyEntity> findActiveKey();

    /**
     * 查询所有可用于验证的密钥(ACTIVE和ROTATING状态)
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    Flux<JwtKeyEntity> findUsableKeys();

    /**
     * 查询即将过期的密钥(30天内)
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    Flux<JwtKeyEntity> findExpiringSoonKeys();

    /**
     * 更新密钥状态
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param id TODO
     * @param status TODO
     * @return TODO
     */
    Mono<Boolean> updateStatus(Long id, String status);

    /**
     * 将所有ACTIVE状态的密钥设置为ROTATING
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    Mono<Integer> markAllActiveAsRotating();

    /**
     * 删除已吊销且超过保留期的密钥
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param days TODO
     * @return TODO
     */
    Mono<Integer> deleteRevokedKeysOlderThan(int days);
}
