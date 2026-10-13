package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.LoginLogEntity;
import reactor.core.publisher.Mono;

/**
 * 登录日志仓储接口。
 *
 * <p>定义登录日志的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 支持按账号与状态过滤的分页回溯查询，供管理界面与安全审计使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface LoginLogRepository {

    /**
     * 新增登录日志。
     *
     * @param entity 登录日志实体
     * @return 影响行数
     */
    Mono<Integer> insert(LoginLogEntity entity);

    /**
     * 分页查询登录日志。
     *
     * @param username 登录账号，可模糊匹配，可为 {@code null}
     * @param status 登录状态（0-成功，1-失败），可为 {@code null}
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 分页结果，按登录时间倒序
     */
    Mono<PageResponse<LoginLogEntity>> search(String username, Integer status, int pageNum, int pageSize);
}
