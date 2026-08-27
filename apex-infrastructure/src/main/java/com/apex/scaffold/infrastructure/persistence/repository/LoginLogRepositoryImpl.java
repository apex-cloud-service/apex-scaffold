package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.LoginLogEntity;
import com.apex.scaffold.domain.repository.LoginLogRepository;
import com.apex.scaffold.infrastructure.persistence.converter.LoginLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.LoginLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.LoginLogPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link LoginLogRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link LoginLogEntity} 的持久化读写与分页回溯查询，通过
 * {@link LoginLogPoConverter} 在领域实体与 {@link LoginLogPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LoginLogRepository
 */
@Repository
@RequiredArgsConstructor
public class LoginLogRepositoryImpl implements LoginLogRepository {

    /**
     * 登录日志 Mapper。
     */
    private final LoginLogMapper loginLogMapper;

    /**
     * 登录日志实体与持久化对象转换器。
     */
    private final LoginLogPoConverter loginLogPoConverter;

    /**
     * 新增登录日志。
     *
     * @param entity 登录日志实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(LoginLogEntity entity) {
        return Mono.fromCallable(() -> loginLogMapper.insert(loginLogPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 分页查询登录日志。
     *
     * @param username 登录账号，可模糊匹配，可为 {@code null}
     * @param status 登录状态（0-成功，1-失败），可为 {@code null}
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 分页结果，按登录时间倒序
     */
    @Override
    public Mono<PageResponse<LoginLogEntity>> search(String username, Integer status, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    Page<LoginLogPO> page = new Page<>(pageNum, pageSize);
                    loginLogMapper.selectPage(
                            page,
                            ConditionUtil.lambda(LoginLogPO.class)
                                    .likeIfPresent(LoginLogPO::getUsername, username)
                                    .eqIfPresent(LoginLogPO::getStatus, status)
                                    .orderByDesc(LoginLogPO::getLoginTime));

                    List<LoginLogEntity> entities = loginLogPoConverter.toEntityList(page.getRecords());
                    return PageResponse.of(entities, page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
