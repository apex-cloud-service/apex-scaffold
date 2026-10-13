package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.model.entity.UserSocialEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户社交身份绑定仓储接口（领域层 Port）。
 *
 * <p>定义 {@link UserSocialEntity} 的持久化契约，由基础设施层提供
 * MyBatis-Plus 实现。用于多端登录时的账户归一查询（按渠道 OpenID / UnionID
 * 定位账户）与绑定记录维护。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserSocialEntity
 */
public interface UserSocialRepository {

    /**
     * 按渠道与 OpenID 查询绑定记录。
     *
     * <p>多端登录归一算法的第一步：以（渠道, OpenID）定位已绑定账户。
     *
     * @param channel 社交渠道
     * @param openId  该渠道下的 OpenID
     * @return 绑定记录；不存在时返回 {@link Mono#empty()}
     */
    Mono<UserSocialEntity> findByChannelAndOpenId(SocialChannel channel, String openId);

    /**
     * 按 UnionID 查询任一绑定记录。
     *
     * <p>归一算法第二步：同一微信开放平台主体下各端 UnionID 相同，
     * 命中任意一条即认为该微信自然人已有账户。
     *
     * @param unionId 微信 UnionID
     * @return 绑定记录；不存在时返回 {@link Mono#empty()}
     */
    Mono<UserSocialEntity> findByUnionId(String unionId);

    /**
     * 查询用户名下全部绑定记录。
     *
     * <p>用于账户详情展示与绑定渠道清点。
     *
     * @param userId 用户 ID（sys_user.id）
     * @return 绑定记录流
     */
    Flux<UserSocialEntity> findByUserId(Long userId);

    /**
     * 保存绑定记录。
     *
     * <p>新记录执行插入；携带 ID 的记录执行更新。
     *
     * @param entity 绑定实体
     * @return 保存后的实体
     */
    Mono<UserSocialEntity> save(UserSocialEntity entity);
}
