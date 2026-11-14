package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserConsentEntity;
import com.apex.scaffold.domain.repository.UserConsentRepository;
import com.apex.scaffold.infrastructure.persistence.converter.UserConsentPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserConsentMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserConsentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 用户同意记录仓库实现（基础设施层）。
 *
 * <p>负责 {@link UserConsentEntity} 的持久化读写，并通过 {@link UserConsentPoConverter}
 * 完成领域实体与持久化对象之间的转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserConsentRepository
 */
@Repository
@RequiredArgsConstructor
public class UserConsentRepositoryImpl implements UserConsentRepository {

    private final UserConsentMapper mapper;
    private final UserConsentPoConverter userConsentPoConverter;

    /**
     * 保存用户同意记录（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     *
     * @param entity 用户同意记录实体
     * @return 保存后的用户同意记录实体
     */
    @Override
    public Mono<UserConsentEntity> save(UserConsentEntity entity) {
        return Mono.fromCallable(() -> {
                    UserConsentPO po = userConsentPoConverter.toPo(entity);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(userConsentPoConverter::toEntity);
    }
}
