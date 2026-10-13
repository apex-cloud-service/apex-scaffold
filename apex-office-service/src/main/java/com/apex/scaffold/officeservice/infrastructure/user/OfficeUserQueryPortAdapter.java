package com.apex.scaffold.officeservice.infrastructure.user;

import com.apex.scaffold.office.application.port.OfficeUserQueryPort;
import com.apex.scaffold.office.application.port.OfficeUserSnapshot;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公模块用户查询出站端口的降级适配器（Phase 5 过渡实现）。
 *
 * <p>迁移方案 Phase 5：办公能力从单体抽取为独立服务 {@code apex-office-service} 后，
 * 办公应用层 {@link OfficeUserQueryPort} 的出站端口在本服务内暂无真实数据源——
 * 用户档案属于系统域，需经 {@code apex-system-service} 的内部契约
 * {@code GET /internal/v1/users/{accountId}}（迁移方案 §6.2）查询；该内部 API 与
 * {@code SystemFeignClient} 桥接在 Phase 6 才落地，且本服务不持有用户表。</p>
 *
 * <h2>降级语义（迁移方案 §6.3）</h2>
 * <p>按 §6.3 降级策略矩阵，system 用户档案依赖的语义为"快速失败"。
 * 在本降级实现中：</p>
 * <ul>
 *   <li>{@link #getById(Long)}：返回空 Mono，使上层 {@code OfficeApplicationService.login}
 *       的校验链在 {@code switchIfEmpty(denied())} 处终止，对外呈现为登录失败
 *       （{@code LOGIN_FAILED}），fail-closed，不泄露内部原因、不放行任何用户</li>
 *   <li>{@link #updateLoginInfo(Long, String)}：返回 {@code false}，表示未记录登录信息，
 *       不阻断登录主链路（写操作可降级）</li>
 * </ul>
 *
 * <p><b>升级路径（Phase 6）</b>：待 system-service 内部用户快照接口就绪后，以
 * Feign 客户端（{@code SystemFeignClient}）+ {@code BlockingIoSchedulers.remote()}
 * 桥接的适配器替换本类，即可恢复真实用户校验；届时本降级 Bean 应被移除或转为兜底
 * FallbackFactory。注意 {@code UserSnapshotDTO} 当前不含 {@code openId}，升级时需
 * 同步扩展内部契约字段。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see OfficeUserQueryPort
 */
@Slf4j
@Service
public class OfficeUserQueryPortAdapter implements OfficeUserQueryPort {

    /** 迁移完成前仅告警一次，避免每次登录都刷屏。 */
    private final AtomicBoolean degradedWarningLogged = new AtomicBoolean(false);

    /**
     * 按用户 ID 查询用户快照（降级：恒返回空 Mono）。
     *
     * <p>当前无真实用户数据源，返回空 Mono 使登录校验链 fail-closed。</p>
     *
     * @param userId 用户 ID
     * @return 恒定空 Mono
     */
    @Override
    public Mono<OfficeUserSnapshot> getById(Long userId) {
        if (degradedWarningLogged.compareAndSet(false, true)) {
            log.warn(
                    "OfficeUserQueryPort 降级生效：暂无系统域用户快照数据源，"
                            + "办公平台登录用户校验将 fail-closed；待 Phase 6 内部用户 API 桥接后恢复。userId={}",
                    userId);
        }
        return Mono.empty();
    }

    /**
     * 更新用户最后登录信息（降级：恒返回 false）。
     *
     * <p>登录信息写入为旁路操作，降级返回 false 不阻断登录主链路。</p>
     *
     * @param userId  用户 ID
     * @param loginIp 客户端登录 IP
     * @return 恒定 false
     */
    @Override
    public Mono<Boolean> updateLoginInfo(Long userId, String loginIp) {
        return Mono.just(false);
    }
}
