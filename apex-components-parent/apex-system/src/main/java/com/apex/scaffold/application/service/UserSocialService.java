package com.apex.scaffold.application.service;

import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.model.entity.UserSocialEntity;
import com.apex.scaffold.domain.repository.UserSocialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户社交身份绑定应用服务。
 *
 * <p>封装 {@link UserSocialEntity} 的查询与幂等绑定维护，供多端登录链路
 * 在认证成功后登记渠道身份。绑定为幂等操作：同一渠道 OpenID 重复绑定时
 * 仅同步 UnionID，不产生重复记录；渠道身份已被其他账户绑定时跳过并告警。
 *
 * <h2>设计说明</h2>
 * <p>本服务向调用方传播仓储异常；登录链路调用方应以
 * {@code onErrorResume} 容忍绑定失败（仅告警不阻断登录），绑定缺失可在
 * 下次登录时补写，历史数据由迁移脚本回填。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserSocialRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSocialService {

    /** 社交身份绑定仓储。 */
    private final UserSocialRepository userSocialRepository;

    /**
     * 按渠道与 OpenID 查询绑定记录。
     *
     * @param channel 社交渠道
     * @param openId  该渠道下的 OpenID
     * @return 绑定记录；不存在时返回 {@link Mono#empty()}
     */
    public Mono<UserSocialEntity> findByChannelAndOpenId(SocialChannel channel, String openId) {
        return userSocialRepository.findByChannelAndOpenId(channel, openId);
    }

    /**
     * 按 UnionID 查询任一绑定记录（跨渠道账户归一依据）。
     *
     * @param unionId 微信 UnionID
     * @return 绑定记录；不存在时返回 {@link Mono#empty()}
     */
    public Mono<UserSocialEntity> findByUnionId(String unionId) {
        return userSocialRepository.findByUnionId(unionId);
    }

    /**
     * 查询用户名下全部绑定记录。
     *
     * @param userId 用户 ID
     * @return 绑定记录流
     */
    public Flux<UserSocialEntity> findByUserId(Long userId) {
        return userSocialRepository.findByUserId(userId);
    }

    /**
     * 幂等登记渠道身份绑定。
     *
     * <p>处理规则：
     * <ul>
     *   <li>（渠道, OpenID）尚未绑定 → 新建绑定记录</li>
     *   <li>已绑定到同一用户 → 仅同步 UnionID</li>
     *   <li>已绑定到其他用户 → 跳过并记录 WARN（唯一约束兜底，不覆盖既有归属）</li>
     *   <li>入参缺失（userId/openId 为空）→ 静默跳过</li>
     * </ul>
     *
     * @param userId   用户 ID（sys_user.id）
     * @param tenantId 租户 ID，可为 {@code null}
     * @param channel  社交渠道
     * @param openId   该渠道下的 OpenID
     * @param unionId  微信 UnionID，可为 {@code null}
     */
    public Mono<Void> upsertBinding(
            Long userId, String tenantId, SocialChannel channel, String openId, String unionId) {
        if (userId == null || channel == null || openId == null || openId.isBlank()) {
            return Mono.empty();
        }
        return userSocialRepository
                .findByChannelAndOpenId(channel, openId)
                .flatMap(existing -> {
                    if (userId.equals(existing.getUserId())) {
                        existing.syncUnionId(unionId);
                        return userSocialRepository.save(existing).thenReturn(Boolean.TRUE);
                    }
                    log.warn(
                            "社交身份已绑定其他用户，跳过重复绑定: channel={}, existingUserId={}, requestUserId={}",
                            channel,
                            existing.getUserId(),
                            userId);
                    return Mono.just(Boolean.TRUE);
                })
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(handled -> Boolean.TRUE.equals(handled)
                        ? Mono.<Void>empty()
                        : userSocialRepository
                                .save(UserSocialEntity.bind(userId, tenantId, channel, openId, unionId))
                                .then());
    }
}
