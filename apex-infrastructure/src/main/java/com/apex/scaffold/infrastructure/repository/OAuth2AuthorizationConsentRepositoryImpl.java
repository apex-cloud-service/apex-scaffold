package com.apex.scaffold.infrastructure.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import com.apex.scaffold.domain.repository.OAuth2AuthorizationConsentRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.OAuth2AuthorizationConsentMapper;
import com.apex.scaffold.infrastructure.persistence.po.OAuth2AuthorizationConsentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * OAuth2 授权同意仓储实现
 *
 * <p>管理 OAuth2 授权同意信息的持久化操作
 *
 * @author Apex Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OAuth2AuthorizationConsentRepositoryImpl implements OAuth2AuthorizationConsentRepository {

    private final OAuth2AuthorizationConsentMapper mapper;

    @Override
    public Mono<OAuth2AuthorizationConsentEntity> findByClientIdAndUserId(String clientId, String userId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<OAuth2AuthorizationConsentPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(OAuth2AuthorizationConsentPO::getRegisteredClientId, clientId)
                            .eq(OAuth2AuthorizationConsentPO::getPrincipalName, userId);
                    OAuth2AuthorizationConsentPO po = mapper.selectOne(wrapper);
                    return po != null ? toEntity(po, clientId, userId) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<OAuth2AuthorizationConsentEntity> save(OAuth2AuthorizationConsentEntity consent) {
        return Mono.fromCallable(() -> {
                    OAuth2AuthorizationConsentPO po = convertToPo(consent);

                    LambdaQueryWrapper<OAuth2AuthorizationConsentPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(OAuth2AuthorizationConsentPO::getRegisteredClientId, consent.getClientId())
                            .eq(OAuth2AuthorizationConsentPO::getPrincipalName, consent.getUserId());
                    OAuth2AuthorizationConsentPO existing = mapper.selectOne(wrapper);

                    if (existing != null) {
                        po.setId(existing.getId());
                        po.setCreatedAt(existing.getCreatedAt());
                        po.setUpdatedAt(Instant.now());
                        mapper.updateById(po);
                        log.info("更新OAuth2授权同意: clientId={}, userId={}", consent.getClientId(), consent.getUserId());
                    } else {
                        po.setId(UUID.randomUUID().toString());
                        po.setCreatedAt(Instant.now());
                        po.setUpdatedAt(Instant.now());
                        mapper.insert(po);
                        log.info("创建OAuth2授权同意: clientId={}, userId={}", consent.getClientId(), consent.getUserId());
                    }

                    return consent;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Void> deleteByClientIdAndUserId(String clientId, String userId) {
        return Mono.fromRunnable(() -> {
                    LambdaQueryWrapper<OAuth2AuthorizationConsentPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(OAuth2AuthorizationConsentPO::getRegisteredClientId, clientId)
                            .eq(OAuth2AuthorizationConsentPO::getPrincipalName, userId);
                    mapper.delete(wrapper);
                    log.info("删除OAuth2授权同意: clientId={}, userId={}", clientId, userId);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    private OAuth2AuthorizationConsentEntity toEntity(OAuth2AuthorizationConsentPO po, String clientId, String userId) {
        OAuth2AuthorizationConsentEntity entity = new OAuth2AuthorizationConsentEntity();
        entity.setId(po.getId());
        entity.setClientId(clientId);
        entity.setUserId(userId);

        if (po.getAuthorities() != null && !po.getAuthorities().isEmpty()) {
            Set<String> scopes = Arrays.stream(po.getAuthorities().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            entity.setAuthorizedScopes(scopes);
        } else {
            entity.setAuthorizedScopes(new HashSet<>());
        }

        entity.setGrantedAt(po.getCreatedAt());
        entity.setLastModifiedAt(po.getUpdatedAt());
        return entity;
    }

    private OAuth2AuthorizationConsentPO convertToPo(OAuth2AuthorizationConsentEntity entity) {
        OAuth2AuthorizationConsentPO po = new OAuth2AuthorizationConsentPO();
        po.setId(entity.getId());
        po.setRegisteredClientId(entity.getClientId());
        po.setPrincipalName(entity.getUserId());

        if (entity.getAuthorizedScopes() != null
                && !entity.getAuthorizedScopes().isEmpty()) {
            po.setAuthorities(String.join(",", entity.getAuthorizedScopes()));
        } else {
            po.setAuthorities("");
        }

        return po;
    }
}
