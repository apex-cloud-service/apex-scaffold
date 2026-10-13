package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import com.apex.scaffold.domain.repository.UserProfileRepository;
import com.apex.scaffold.infrastructure.persistence.converter.UserProfilePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserProfileMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserProfilePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 用户资料仓库实现（基础设施层）。
 *
 * <p>负责 {@link UserProfileEntity} 的持久化读写，通过 MyBatis-Plus Mapper 访问数据库，
 * 并使用 {@link UserProfilePoConverter} 在领域实体与持久化对象之间转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserProfileRepository
 */
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileMapper mapper;
    private final UserProfilePoConverter userProfilePoConverter;

    /**
     * 根据用户 ID 查询资料信息。
     *
     * @param userId 用户 ID
     * @return 用户资料实体；若不存在则返回空 Mono
     */
    @Override
    public Mono<UserProfileEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<UserProfilePO>()
                        .eq(UserProfilePO::getUserId, userId)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(userProfilePoConverter::toEntity);
    }

    /**
     * 保存用户资料（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     *
     * @param entity 用户资料实体
     * @return 保存后的用户资料实体
     */
    @Override
    public Mono<UserProfileEntity> save(UserProfileEntity entity) {
        return Mono.fromCallable(() -> {
                    UserProfilePO po = userProfilePoConverter.toPo(entity);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(userProfilePoConverter::toEntity);
    }
}
