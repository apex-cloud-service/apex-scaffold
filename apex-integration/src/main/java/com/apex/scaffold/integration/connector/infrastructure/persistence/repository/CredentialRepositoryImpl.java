package com.apex.scaffold.integration.connector.infrastructure.persistence.repository;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.connector.domain.Credential;
import com.apex.scaffold.integration.connector.domain.repository.CredentialRepository;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.CredentialMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.CredentialPO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link CredentialRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E2）。
 *
 * <p>轮换语义：{@code versionNo = 当前版本 + 1}，刷新密文/IV 与 {@code rotatedAt}。
 * 仓储层不接触明文，仅搬运密文与 IV。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {

    private final CredentialMapper mapper;

    @Override
    public Mono<Credential> findByKey(String tenantId, String credentialKey) {
        return Mono.fromCallable(() -> mapper.selectOne(ConditionUtil.lambda(CredentialPO.class)
                        .eq(CredentialPO::getTenantId, tenantId)
                        .eq(CredentialPO::getCredentialKey, credentialKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Credential> findById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(po -> po != null && tenantId != null && tenantId.equals(po.getTenantId()))
                .map(this::toEntity);
    }

    @Override
    public Mono<List<Credential>> list(String tenantId, String credentialType) {
        return Mono.fromCallable(() -> mapper
                        .selectList(ConditionUtil.lambda(CredentialPO.class)
                                .eqIfPresent(CredentialPO::getCredentialType, credentialType)
                                .eq(CredentialPO::getTenantId, tenantId)
                                .orderByDesc(CredentialPO::getCreateTime))
                        .stream()
                        .map(this::toEntity)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Credential> insert(Credential credential) {
        return Mono.fromCallable(() -> {
                    CredentialPO po = toPo(credential);
                    if (po.getVersionNo() == null) {
                        po.setVersionNo(1);
                    }
                    if (po.getDeleted() == null) {
                        po.setDeleted(0);
                    }
                    try {
                        mapper.insert(po);
                    } catch (DuplicateKeyException e) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR, "凭据标识已存在: " + po.getCredentialKey());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Credential> rotate(Credential credential) {
        return Mono.fromCallable(() -> {
                    CredentialPO po = toPo(credential);
                    po.setVersionNo((po.getVersionNo() == null ? 1 : po.getVersionNo()) + 1);
                    po.setRotatedAt(LocalDateTime.now());
                    int rows = mapper.updateById(po);
                    if (rows == 0) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR, "凭据不存在或已被删除");
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> delete(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.delete(ConditionUtil.lambda(CredentialPO.class)
                        .eq(CredentialPO::getTenantId, tenantId)
                        .eq(CredentialPO::getId, id)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(rows ->
                        rows > 0 ? Mono.empty() : Mono.error(new BusinessException(ResultCode.NOT_FOUND, "凭据不存在")));
    }

    private Credential toEntity(CredentialPO po) {
        Credential credential = new Credential();
        credential.setId(po.getId());
        credential.setTenantId(po.getTenantId());
        credential.setCredentialKey(po.getCredentialKey());
        credential.setCredentialType(po.getCredentialType());
        credential.setSecretCipher(po.getSecretCipher());
        credential.setSecretIv(po.getSecretIv());
        credential.setVersionNo(po.getVersionNo());
        credential.setRotatedAt(po.getRotatedAt());
        credential.setCreateTime(po.getCreateTime());
        credential.setUpdateTime(po.getUpdateTime());
        credential.setDeleted(po.getDeleted());
        return credential;
    }

    private CredentialPO toPo(Credential credential) {
        CredentialPO po = new CredentialPO();
        po.setId(credential.getId());
        po.setTenantId(credential.getTenantId());
        po.setCredentialKey(credential.getCredentialKey());
        po.setCredentialType(credential.getCredentialType());
        po.setSecretCipher(credential.getSecretCipher());
        po.setSecretIv(credential.getSecretIv());
        po.setVersionNo(credential.getVersionNo());
        po.setRotatedAt(credential.getRotatedAt());
        po.setCreateTime(credential.getCreateTime());
        po.setUpdateTime(credential.getUpdateTime());
        po.setDeleted(credential.getDeleted());
        return po;
    }
}
