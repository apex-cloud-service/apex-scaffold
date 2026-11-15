package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

/**
 * 密码找回恢复仓储端口。
 *
 * <p>恢复链路是匿名跨租户的可信服务端流程：本仓储的读写不经过租户作用域包装，
 * 全部以恢复单主键与显式条件访问，不向外暴露批量数据；目标租户由服务端解析，
 * 客户端无法选择或影响。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public interface PasswordRecoveryRepository {

    /**
     * 保存恢复单，返回持久化后的实体。
     *
     * @param entity 待保存的恢复单
     * @return 持久化后的恢复单
     */
    Mono<PasswordRecoveryEntity> save(PasswordRecoveryEntity entity);

    /**
     * 按恢复单主键查询，不附加租户作用域。
     *
     * @param id 恢复单主键
     * @return 恢复单，不存在时为空
     */
    Mono<PasswordRecoveryEntity> findById(String id);

    /**
     * 条件状态跃迁：仅当当前状态等于 fromStatus 时更新为 toStatus，
     * 返回是否由本次调用完成跃迁（并发下最多一次成功）。
     *
     * @param id 恢复单主键
     * @param fromStatus 期望的当前状态
     * @param toStatus 目标状态
     * @return 本次调用完成状态跃迁时为true
     */
    Mono<Boolean> transition(String id, String fromStatus, String toStatus);

    /**
     * 递增验证码校验失败次数，返回递增后的次数。
     *
     * @param id 恢复单主键
     * @return 递增后的验证码校验失败次数
     */
    Mono<Integer> incrementAttemptCount(String id);

    /**
     * 统计指定联系方式摘要在窗口内的申请次数，用于申请侧限流。
     *
     * @param contactHash 联系方式摘要
     * @param since 统计起始时间
     * @return 窗口内的申请次数
     */
    Mono<Long> countRecentByContactHash(String contactHash, LocalDateTime since);

    /**
     * 统计指定来源 IP 摘要在窗口内的申请次数，用于申请侧限流。
     *
     * @param requestIpHash 来源IP摘要
     * @param since 统计起始时间
     * @return 窗口内的申请次数
     */
    Mono<Long> countRecentByIpHash(String requestIpHash, LocalDateTime since);
}
