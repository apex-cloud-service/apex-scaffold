package com.apex.scaffold.infrastructure.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.OAuth2RegisteredClientMapper;
import com.apex.scaffold.infrastructure.persistence.po.OAuth2RegisteredClientPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2注册客户端仓储实现
 *
 * <p>负责OAuth2客户端的数据访问，采用响应式编程模型。
 * 数据库I/O操作调度到{@link BlockingIoSchedulers#single()}线程池。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class OAuth2RegisteredClientRepositoryImpl implements OAuth2RegisteredClientRepository {

    private static final String EMPTY_JSON_ARRAY = "[]";

    private final OAuth2RegisteredClientMapper mapper;

    /**
     * 根据客户端ID查找OAuth2注册客户端
     *
     * @param clientId 客户端ID
     * @return 包含客户端实体的Mono,如果不存在则为空Mono
     */
    @Override
    public Mono<OAuth2RegisteredClientEntity> findByClientId(String clientId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<OAuth2RegisteredClientPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(OAuth2RegisteredClientPO::getClientId, clientId);
                    OAuth2RegisteredClientPO po = mapper.selectOne(wrapper);
                    return po != null ? toEntity(po) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据主键ID查找OAuth2注册客户端
     *
     * @param id 主键ID
     * @return 包含客户端实体的Mono,如果不存在则为空Mono
     */
    @Override
    public Mono<OAuth2RegisteredClientEntity> findById(String id) {
        return Mono.fromCallable(() -> {
                    OAuth2RegisteredClientPO po = mapper.selectById(id);
                    return po != null ? toEntity(po) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 保存或更新OAuth2注册客户端
     *
     * <p>如果客户端ID已存在则执行更新,否则执行插入。
     *
     * @param client 客户端实体
     * @return 包含保存后客户端实体的Mono
     */
    @Override
    public Mono<OAuth2RegisteredClientEntity> save(OAuth2RegisteredClientEntity client) {
        return Mono.fromCallable(() -> {
                    OAuth2RegisteredClientPO po = convertToPo(client);
                    if (client.getId() != null && mapper.selectById(client.getId()) != null) {
                        mapper.updateById(po);
                        log.info("更新OAuth2客户端: clientId={}", client.getClientId());
                    } else {
                        mapper.insert(po);
                        log.info("创建OAuth2客户端: clientId={}", client.getClientId());
                    }
                    return client;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据主键ID删除OAuth2注册客户端
     *
     * @param id 主键ID
     */
    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> {
                    mapper.deleteById(id);
                    log.info("删除OAuth2客户端: id={}", id);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 分页查询所有OAuth2注册客户端
     *
     * @param page 页码,从1开始
     * @param size 每页大小
     * @return 包含客户端实体列表的Flux
     */
    @Override
    public Flux<OAuth2RegisteredClientEntity> findAll(int page, int size) {
        return Mono.fromCallable(() -> {
                    Page<OAuth2RegisteredClientPO> poPage = mapper.selectPage(new Page<>(page, size), null);
                    return poPage.getRecords().stream().map(this::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 统计OAuth2注册客户端总数
     *
     * @return 包含客户端总数的Mono
     */
    @Override
    public Mono<Long> count() {
        return Mono.fromCallable(() -> mapper.selectCount(null)).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据客户端名称模糊查询OAuth2注册客户端
     *
     * @param clientName 客户端名称(支持模糊匹配)
     * @param page 页码,从1开始
     * @param size 每页大小
     * @return 包含匹配客户端实体列表的Flux
     */
    @Override
    public Flux<OAuth2RegisteredClientEntity> findByClientNameLike(String clientName, int page, int size) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<OAuth2RegisteredClientPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.like(OAuth2RegisteredClientPO::getClientName, clientName);
                    Page<OAuth2RegisteredClientPO> poPage = mapper.selectPage(new Page<>(page, size), wrapper);
                    return poPage.getRecords().stream().map(this::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    private OAuth2RegisteredClientEntity toEntity(OAuth2RegisteredClientPO po) {
        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId(po.getId());
        entity.setClientId(po.getClientId());
        entity.setClientIdIssuedAt(
                po.getClientIdIssuedAt() != null
                        ? po.getClientIdIssuedAt()
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                        : null);
        entity.setClientSecret(po.getClientSecret());
        entity.setClientSecretExpiresAt(
                po.getClientSecretExpiresAt() != null
                        ? po.getClientSecretExpiresAt()
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                        : null);
        entity.setClientName(po.getClientName());
        entity.setClientAuthenticationMethods(parseJsonArray(po.getClientAuthenticationMethods()));
        entity.setAuthorizationGrantTypes(parseJsonArray(po.getAuthorizationGrantTypes()));
        entity.setRedirectUris(parseJsonArray(po.getRedirectUris()));
        entity.setPostLogoutRedirectUris(parseJsonArray(po.getPostLogoutRedirectUris()));
        entity.setScopes(parseJsonArray(po.getScopes()));
        entity.setClientSettings(po.getClientSettings());
        entity.setTokenSettings(po.getTokenSettings());
        return entity;
    }

    private OAuth2RegisteredClientPO convertToPo(OAuth2RegisteredClientEntity entity) {
        OAuth2RegisteredClientPO po = new OAuth2RegisteredClientPO();
        po.setId(entity.getId());
        po.setClientId(entity.getClientId());
        po.setClientIdIssuedAt(
                entity.getClientIdIssuedAt() != null
                        ? entity.getClientIdIssuedAt()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                        : null);
        po.setClientSecret(entity.getClientSecret());
        po.setClientSecretExpiresAt(
                entity.getClientSecretExpiresAt() != null
                        ? entity.getClientSecretExpiresAt()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                        : null);
        po.setClientName(entity.getClientName());
        po.setClientAuthenticationMethods(toJsonArray(entity.getClientAuthenticationMethods()));
        po.setAuthorizationGrantTypes(toJsonArray(entity.getAuthorizationGrantTypes()));
        po.setRedirectUris(toJsonArray(entity.getRedirectUris()));
        po.setPostLogoutRedirectUris(toJsonArray(entity.getPostLogoutRedirectUris()));
        po.setScopes(toJsonArray(entity.getScopes()));
        po.setClientSettings(entity.getClientSettings());
        po.setTokenSettings(entity.getTokenSettings());
        return po;
    }

    /**
     * 解析JSON数组字符串为Set集合
     * <p>简单实现：移除方括号和引号后分割，适用于简单字符串数组。
     * 生产环境可考虑使用Jackson或Gson进行完整JSON解析。
     *
     * @param json JSON数组字符串，如: ["value1","value2"]
     * @return Set集合
     */
    private Set<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty() || EMPTY_JSON_ARRAY.equals(json)) {
            return new HashSet<>();
        }
        String cleaned = json.replaceAll("[\\[\\]\"]", "");
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 将Set集合序列化为JSON数组字符串
     *
     * @param set Set集合
     * @return JSON数组字符串
     */
    private String toJsonArray(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", set) + "\"]";
    }
}
