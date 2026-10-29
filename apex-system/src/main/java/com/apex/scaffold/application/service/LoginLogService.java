package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.LoginLogAssembler;
import com.apex.scaffold.application.dto.admin.LoginLogDTO;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.LoginLogEntity;
import com.apex.scaffold.domain.repository.LoginLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 登录日志应用服务。
 *
 * <p>提供登录日志的记录与分页回溯查询能力。日志由认证服务在每次
 * 登录尝试（无论成败）后写入，管理界面按账号与状态筛选回溯，
 * 支撑安全审计与异常登录排查。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    /**
     * 登录日志仓储。
     */
    private final LoginLogRepository loginLogRepository;

    private final LoginLogAssembler loginLogAssembler;

    /**
     * 记录一条登录日志。
     *
     * @param entity 登录日志实体
     */
    public Mono<Void> record(LoginLogEntity entity) {
        return loginLogRepository
                .insert(entity)
                .doOnNext(
                        rows -> log.debug("登录日志已记录，username: {}, status: {}", entity.getUsername(), entity.getStatus()))
                .then();
    }

    /**
     * 分页查询登录日志。
     *
     * @param username 登录账号，可模糊匹配，可为 {@code null}
     * @param status 登录状态（0-成功，1-失败），可为 {@code null}
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小，限制在 1-100
     * @return 登录日志分页结果
     */
    public Mono<PageResponse<LoginLogDTO>> search(String username, Integer status, int pageNum, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), 100);
        return loginLogRepository
                .search(username, status, pageNum, boundedSize)
                .map(page -> PageResponse.of(
                        loginLogAssembler.toDTOList(page.getList()), page.getTotal(), pageNum, boundedSize));
    }
}
