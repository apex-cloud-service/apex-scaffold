package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.PolicyVersionEntity;
import com.apex.scaffold.domain.repository.PolicyVersionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.PolicyVersionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.PolicyVersionMapper;
import com.apex.scaffold.infrastructure.persistence.po.PolicyVersionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 政策版本仓库实现（基础设施层）。
 *
 * <p>负责 {@link PolicyVersionEntity} 的查询逻辑，通过 MyBatis-Plus Mapper 访问数据库，
 * 并使用 {@link PolicyVersionPoConverter} 在领域实体与持久化对象之间转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see PolicyVersionRepository
 */
@Repository
@RequiredArgsConstructor
public class PolicyVersionRepositoryImpl implements PolicyVersionRepository {

    private final PolicyVersionMapper mapper;
    private final PolicyVersionPoConverter policyVersionPoConverter;

    /**
     * 查询指定政策类型的最新版本。
     *
     * <p>按 policyType 过滤并按创建时间倒序取第一条（limit 1）。
     *
     * @param policyType 政策类型（如 USER_AGREEMENT、PRIVACY_POLICY）
     * @return 最新政策版本实体；若不存在则返回空 Mono
     */
    @Override
    /**
     * 查找LatestByType
     *
     * @param policyType 参数
     *
     * @return 结果
     */
    public Mono<PolicyVersionEntity> findLatestByType(String policyType) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<PolicyVersionPO>()
                        .eq(PolicyVersionPO::getPolicyType, policyType)
                        .orderByDesc(PolicyVersionPO::getCreateTime)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(policyVersionPoConverter::toEntity);
    }
}
