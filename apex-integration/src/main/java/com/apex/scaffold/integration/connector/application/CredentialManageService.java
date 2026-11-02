package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.Credential;
import com.apex.scaffold.integration.connector.domain.CredentialCipher;
import com.apex.scaffold.integration.connector.domain.repository.CredentialRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 租户凭据管理用例（方案 M5 / 批次 E2，C7 标准管理接口）。
 *
 * <p>写操作只接收明文入参，落库前经 {@link CredentialCipher} AES-256-GCM 加密
 * （密文与 IV 分列）；仓储与响应永不接触明文，日志不落任何凭据内容。
 * 轮换时 {@code versionNo} 在当前版本上递增并刷新 {@code rotatedAt}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialManageService {

    /** 初始凭据版本。 */
    private static final int INITIAL_VERSION = 1;

    private final CredentialRepository credentialRepository;

    private final CredentialCipher cipher;

    /**
     * 登记新凭据（明文加密后落库）。
     *
     * @param tenantId       租户编码
     * @param credentialKey  凭据标识（租户内唯一）
     * @param credentialType 凭据类型（API_KEY/BASIC/TOKEN/X509）
     * @param plaintext      明文（仅入参瞬时存在，不落日志）
     * @return 落库后的凭据元数据（含密文，不含明文）
     */
    public Mono<Credential> register(String tenantId, String credentialKey, String credentialType, String plaintext) {
        if (credentialKey == null || credentialKey.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "凭据标识不能为空"));
        }
        if (plaintext == null || plaintext.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "凭据内容不能为空"));
        }
        return Mono.fromCallable(() -> {
                    CredentialCipher.EncryptedSecret encrypted = cipher.encrypt(plaintext);
                    Credential credential = new Credential();
                    credential.setTenantId(tenantId);
                    credential.setCredentialKey(credentialKey.trim());
                    credential.setCredentialType(
                            credentialType == null || credentialType.isBlank() ? "API_KEY" : credentialType.trim());
                    credential.setSecretCipher(encrypted.cipherText());
                    credential.setSecretIv(encrypted.iv());
                    credential.setVersionNo(INITIAL_VERSION);
                    credential.setRotatedAt(LocalDateTime.now());
                    return credential;
                })
                .flatMap(credentialRepository::insert)
                .doOnSuccess(saved -> log.info(
                        "凭据已登记: credentialKey={}, type={}, versionNo={}",
                        saved.getCredentialKey(),
                        saved.getCredentialType(),
                        saved.getVersionNo()));
    }

    /**
     * 轮换凭据（新明文加密后更新，{@code versionNo} 递增）。
     *
     * @param tenantId    租户编码
     * @param id          主键
     * @param newPlaintext 新明文
     * @return 轮换后的凭据
     */
    public Mono<Credential> rotate(String tenantId, Long id, String newPlaintext) {
        if (newPlaintext == null || newPlaintext.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "凭据内容不能为空"));
        }
        return credentialRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "凭据不存在")))
                .flatMap(existing -> Mono.fromCallable(() -> {
                    CredentialCipher.EncryptedSecret encrypted = cipher.encrypt(newPlaintext);
                    existing.setSecretCipher(encrypted.cipherText());
                    existing.setSecretIv(encrypted.iv());
                    return existing;
                }))
                .flatMap(credentialRepository::rotate)
                .doOnSuccess(rotated -> log.info(
                        "凭据已轮换: credentialKey={}, versionNo={}", rotated.getCredentialKey(), rotated.getVersionNo()));
    }

    /**
     * 按 ID 查询凭据。
     *
     * <p>返回实体含密文字段；接口层必须裁剪密文/IV 后再输出，绝不回明文。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 凭据；跨租户访问视为不存在
     */
    public Mono<Credential> get(String tenantId, Long id) {
        return credentialRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "凭据不存在")));
    }

    /**
     * 查询租户凭据列表。
     *
     * @param tenantId       租户编码
     * @param credentialType 凭据类型（可空）
     * @return 凭据列表（含密文，接口层裁剪后输出）
     */
    public Mono<List<Credential>> list(String tenantId, String credentialType) {
        return credentialRepository.list(tenantId, credentialType);
    }

    /**
     * 逻辑删除凭据。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 完成信号
     */
    public Mono<Void> delete(String tenantId, Long id) {
        return credentialRepository.delete(tenantId, id);
    }
}
