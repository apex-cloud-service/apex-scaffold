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

    Mono<PasswordRecoveryEntity> save(PasswordRecoveryEntity entity);

    Mono<PasswordRecoveryEntity> findById(String id);

    /**
     * 条件状态跃迁：仅当当前状态等于 fromStatus 时更新为 toStatus，
     * 返回是否由本次调用完成跃迁（并发下最多一次成功）。
     */
    Mono<Boolean> transition(String id, String fromStatus, String toStatus);

    /** 递增验证码校验失败次数，返回递增后的次数。 */
    Mono<Integer> incrementAttemptCount(String id);

    /** 统计指定联系方式摘要在窗口内的申请次数，用于申请侧限流。 */
    Mono<Long> countRecentByContactHash(String contactHash, LocalDateTime since);

    /** 统计指定来源 IP 摘要在窗口内的申请次数，用于申请侧限流。 */
    Mono<Long> countRecentByIpHash(String requestIpHash, LocalDateTime since);
}
