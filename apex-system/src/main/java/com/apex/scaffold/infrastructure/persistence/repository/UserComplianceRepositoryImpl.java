package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.domain.repository.UserComplianceRepository;
import com.apex.scaffold.infrastructure.persistence.converter.UserCompliancePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserComplianceMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserCompliancePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 用户合规仓库实现（基础设施层）。
 *
 * <p>负责 {@link UserComplianceEntity} 的持久化读写，并通过 {@link UserCompliancePoConverter}
 * 完成领域实体与持久化对象之间的转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserComplianceRepository
 */
@Repository
@RequiredArgsConstructor
public class UserComplianceRepositoryImpl implements UserComplianceRepository {

    private final UserComplianceMapper mapper;
    private final UserCompliancePoConverter userCompliancePoConverter;

    /**
     * 根据用户 ID 查询合规信息。
     *
     * @param userId 用户 ID
     * @return 用户合规实体；若不存在则返回空 Mono
     */
    @Override
    public Mono<UserComplianceEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<UserCompliancePO>()
                        .eq(UserCompliancePO::getUserId, userId)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(userCompliancePoConverter::toEntity);
    }

    /**
     * 保存用户合规信息（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     *
     * @param entity 用户合规实体
     * @return 保存后的用户合规实体
     */
    @Override
    public Mono<UserComplianceEntity> save(UserComplianceEntity entity) {
        return Mono.fromCallable(() -> {
                    UserCompliancePO po = userCompliancePoConverter.toPo(entity);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(userCompliancePoConverter::toEntity);
    }
}
