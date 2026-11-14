package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.model.entity.UserSocialEntity;
import com.apex.scaffold.domain.repository.UserSocialRepository;
import com.apex.scaffold.infrastructure.persistence.converter.UserSocialPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserSocialMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserSocialPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link UserSocialRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link UserSocialEntity} 的持久化读写，通过
 * {@link UserSocialPoConverter} 在领域实体与 {@link UserSocialPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserSocialRepository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserSocialRepositoryImpl implements UserSocialRepository {

    /**
     * 社交身份绑定 Mapper。
     */
    private final UserSocialMapper userSocialMapper;

    /**
     * 社交身份绑定实体与持久化对象转换器。
     */
    private final UserSocialPoConverter userSocialPoConverter;

    @Override
    public Mono<UserSocialEntity> findByChannelAndOpenId(SocialChannel channel, String openId) {
        return Mono.fromCallable(() -> {
                    UserSocialPO po = userSocialMapper.selectOne(ConditionUtil.lambda(UserSocialPO.class)
                            .eqIfPresent(UserSocialPO::getChannel, channel == null ? null : channel.name())
                            .eqIfPresent(UserSocialPO::getOpenId, openId)
                            .last("LIMIT 1"));
                    return po == null ? null : userSocialPoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<UserSocialEntity> findByUnionId(String unionId) {
        return Mono.fromCallable(() -> {
                    UserSocialPO po = userSocialMapper.selectOne(ConditionUtil.lambda(UserSocialPO.class)
                            .eqIfPresent(UserSocialPO::getUnionId, unionId)
                            .orderByAsc(UserSocialPO::getBoundAt)
                            .last("LIMIT 1"));
                    return po == null ? null : userSocialPoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<UserSocialEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> {
                    List<UserSocialPO> records = userSocialMapper.selectList(ConditionUtil.lambda(UserSocialPO.class)
                            .eqIfPresent(UserSocialPO::getUserId, userId)
                            .orderByAsc(UserSocialPO::getBoundAt));
                    return userSocialPoConverter.toEntityList(records);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<UserSocialEntity> save(UserSocialEntity entity) {
        return Mono.fromCallable(() -> {
                    if (entity.getId() == null) {
                        userSocialMapper.insert(userSocialPoConverter.toPo(entity));
                        return entity;
                    }
                    UserSocialPO po = userSocialPoConverter.toPo(entity);
                    userSocialMapper.updateById(po);
                    return userSocialPoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
