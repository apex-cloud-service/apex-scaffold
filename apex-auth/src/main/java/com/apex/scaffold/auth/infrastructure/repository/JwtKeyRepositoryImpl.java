package com.apex.scaffold.auth.infrastructure.repository;

import com.apex.scaffold.auth.domain.model.entity.JwtKeyEntity;
import com.apex.scaffold.auth.domain.repository.JwtKeyRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT密钥仓储实现
 *
 * @author Apex Team
 * @since 2026-09-25
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JwtKeyRepositoryImpl implements JwtKeyRepository {

    private final DatabaseClient databaseClient;

    /**
     * 保存JWT密钥
     *
     * <p>密钥ID为空时执行插入，否则执行更新。
     *
     * @param jwtKey 待保存的JWT密钥实体
     * @return 保存后的JWT密钥实体
     */
    @Override
    public Mono<JwtKeyEntity> save(JwtKeyEntity jwtKey) {
        if (jwtKey.getId() == null) {
            return insert(jwtKey);
        } else {
            return update(jwtKey);
        }
    }

    private Mono<JwtKeyEntity> insert(JwtKeyEntity jwtKey) {
        String sql =
                """
                INSERT INTO jwt_keys (key_version, encrypted_secret, algorithm, status,
                    activated_at, expires_at, rotation_reason, operator_user_id,
                    operator_username, created_at, updated_at, remark)
                VALUES (:keyVersion, :encryptedSecret, :algorithm, :status,
                    :activatedAt, :expiresAt, :rotationReason, :operatorUserId,
                    :operatorUsername, :createdAt, :updatedAt, :remark)
                """;

        return databaseClient
                .sql(sql)
                .bind("keyVersion", jwtKey.getKeyVersion())
                .bind("encryptedSecret", jwtKey.getEncryptedSecret())
                .bind("algorithm", jwtKey.getAlgorithm())
                .bind("status", jwtKey.getStatus())
                .bind("activatedAt", jwtKey.getActivatedAt())
                .bind("expiresAt", jwtKey.getExpiresAt())
                .bind("rotationReason", jwtKey.getRotationReason())
                .bind("operatorUserId", jwtKey.getOperatorUserId())
                .bind("operatorUsername", jwtKey.getOperatorUsername())
                .bind("createdAt", jwtKey.getCreatedAt())
                .bind("updatedAt", jwtKey.getUpdatedAt())
                .bind("remark", jwtKey.getRemark() != null ? jwtKey.getRemark() : "")
                .fetch()
                .rowsUpdated()
                .thenReturn(jwtKey);
    }

    private Mono<JwtKeyEntity> update(JwtKeyEntity jwtKey) {
        String sql =
                """
                UPDATE jwt_keys SET
                    status = :status,
                    deprecated_at = :deprecatedAt,
                    revoked_at = :revokedAt,
                    updated_at = :updatedAt,
                    remark = :remark
                WHERE id = :id
                """;

        return databaseClient
                .sql(sql)
                .bind("status", jwtKey.getStatus())
                .bind("deprecatedAt", jwtKey.getDeprecatedAt())
                .bind("revokedAt", jwtKey.getRevokedAt())
                .bind("updatedAt", jwtKey.getUpdatedAt())
                .bind("remark", jwtKey.getRemark() != null ? jwtKey.getRemark() : "")
                .bind("id", jwtKey.getId())
                .fetch()
                .rowsUpdated()
                .thenReturn(jwtKey);
    }

    /**
     * 根据ID查询JWT密钥
     *
     * @param id 密钥ID
     * @return 匹配的JWT密钥实体，不存在时返回空的Mono
     */
    @Override
    public Mono<JwtKeyEntity> findById(Long id) {
        String sql = "SELECT * FROM jwt_keys WHERE id = :id";
        return databaseClient.sql(sql).bind("id", id).map(this::mapRow).one();
    }

    /**
     * 根据版本号查询JWT密钥
     *
     * @param keyVersion 密钥版本号
     * @return 匹配的JWT密钥实体，不存在时返回空的Mono
     */
    @Override
    public Mono<JwtKeyEntity> findByKeyVersion(String keyVersion) {
        String sql = "SELECT * FROM jwt_keys WHERE key_version = :keyVersion";
        return databaseClient
                .sql(sql)
                .bind("keyVersion", keyVersion)
                .map(this::mapRow)
                .one();
    }

    /**
     * 查询当前活跃的密钥
     *
     * @return 最近激活的ACTIVE状态密钥实体，不存在时返回空的Mono
     */
    @Override
    public Mono<JwtKeyEntity> findActiveKey() {
        String sql = "SELECT * FROM jwt_keys WHERE status = 'ACTIVE' ORDER BY activated_at DESC LIMIT 1";
        return databaseClient.sql(sql).map(this::mapRow).one();
    }

    /**
     * 查询所有可用于验证的密钥（ACTIVE和ROTATING状态）
     *
     * @return 可用密钥实体流，按激活时间倒序排列
     */
    @Override
    public Flux<JwtKeyEntity> findUsableKeys() {
        String sql = "SELECT * FROM jwt_keys WHERE status IN ('ACTIVE', 'ROTATING') ORDER BY activated_at DESC";
        return databaseClient.sql(sql).map(this::mapRow).all();
    }

    /**
     * 查询即将过期的ACTIVE状态密钥（30天内到期）
     *
     * @return 即将过期的密钥实体流，按过期时间正序排列
     */
    @Override
    public Flux<JwtKeyEntity> findExpiringSoonKeys() {
        String sql =
                """
                SELECT * FROM jwt_keys
                WHERE status = 'ACTIVE'
                AND expires_at <= :expirationThreshold
                ORDER BY expires_at ASC
                """;
        LocalDateTime threshold = LocalDateTime.now().plusDays(30);
        return databaseClient
                .sql(sql)
                .bind("expirationThreshold", threshold)
                .map(this::mapRow)
                .all();
    }

    /**
     * 更新密钥状态
     *
     * @param id 密钥ID
     * @param status 目标状态（如ACTIVE、ROTATING、REVOKED）
     * @return 更新成功返回true，密钥不存在时返回false
     */
    @Override
    public Mono<Boolean> updateStatus(Long id, String status) {
        String sql = "UPDATE jwt_keys SET status = :status, updated_at = :updatedAt WHERE id = :id";
        return databaseClient
                .sql(sql)
                .bind("status", status)
                .bind("updatedAt", LocalDateTime.now())
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .map(count -> count > 0);
    }

    /**
     * 将所有ACTIVE状态的密钥批量设置为ROTATING
     *
     * @return 受影响的密钥数量
     */
    @Override
    public Mono<Integer> markAllActiveAsRotating() {
        String sql = "UPDATE jwt_keys SET status = 'ROTATING', updated_at = :updatedAt WHERE status = 'ACTIVE'";
        return databaseClient
                .sql(sql)
                .bind("updatedAt", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .map(Long::intValue);
    }

    /**
     * 删除已吊销且吊销时间超过保留期的密钥
     *
     * @param days 保留天数（以吊销时间为基准向前推算）
     * @return 删除的密钥数量
     */
    @Override
    public Mono<Integer> deleteRevokedKeysOlderThan(int days) {
        String sql = "DELETE FROM jwt_keys WHERE status = 'REVOKED' AND revoked_at < :cutoffDate";
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        return databaseClient
                .sql(sql)
                .bind("cutoffDate", cutoffDate)
                .fetch()
                .rowsUpdated()
                .map(Long::intValue);
    }

    private JwtKeyEntity mapRow(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return JwtKeyEntity.builder()
                .id(row.get("id", Long.class))
                .keyVersion(row.get("key_version", String.class))
                .encryptedSecret(row.get("encrypted_secret", String.class))
                .algorithm(row.get("algorithm", String.class))
                .status(row.get("status", String.class))
                .activatedAt(row.get("activated_at", LocalDateTime.class))
                .expiresAt(row.get("expires_at", LocalDateTime.class))
                .deprecatedAt(row.get("deprecated_at", LocalDateTime.class))
                .revokedAt(row.get("revoked_at", LocalDateTime.class))
                .rotationReason(row.get("rotation_reason", String.class))
                .operatorUserId(row.get("operator_user_id", Long.class))
                .operatorUsername(row.get("operator_username", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .remark(row.get("remark", String.class))
                .build();
    }
}
