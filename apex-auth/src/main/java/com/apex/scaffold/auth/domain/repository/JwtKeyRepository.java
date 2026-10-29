package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.JwtKeyEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT密钥仓储接口
 *
 * @author Apex Team
 * @since 1.3.1
 */
public interface JwtKeyRepository {

    /**
     * 保存JWT密钥
     *
     * @param jwtKey 密钥实体
     * @return 保存后的密钥实体
     */
    Mono<JwtKeyEntity> save(JwtKeyEntity jwtKey);

    /**
     * 根据ID查询
     *
     * @param id 密钥ID
     * @return 匹配的密钥实体，不存在时为空
     */
    Mono<JwtKeyEntity> findById(Long id);

    /**
     * 根据版本号查询
     *
     * @param keyVersion 密钥版本号
     * @return 匹配的密钥实体，不存在时为空
     */
    Mono<JwtKeyEntity> findByKeyVersion(String keyVersion);

    /**
     * 查询当前活跃的密钥
     *
     * @return 当前 ACTIVE 状态的密钥，不存在时为空
     */
    Mono<JwtKeyEntity> findActiveKey();

    /**
     * 查询所有可用于验证的密钥(ACTIVE和ROTATING状态)
     *
     * @return 可用于验证的密钥列表
     */
    Flux<JwtKeyEntity> findUsableKeys();

    /**
     * 查询即将过期的密钥(30天内)
     *
     * @return 即将过期的密钥列表
     */
    Flux<JwtKeyEntity> findExpiringSoonKeys();

    /**
     * 更新密钥状态
     *
     * @param id 密钥ID
     * @param status 目标状态
     * @return 是否更新成功
     */
    Mono<Boolean> updateStatus(Long id, String status);

    /**
     * 将所有ACTIVE状态的密钥设置为ROTATING
     *
     * @return 受影响的密钥数量
     */
    Mono<Integer> markAllActiveAsRotating();

    /**
     * 删除已吊销且超过保留期的密钥
     *
     * @param days 保留期天数
     * @return 删除的密钥数量
     */
    Mono<Integer> deleteRevokedKeysOlderThan(int days);
}
