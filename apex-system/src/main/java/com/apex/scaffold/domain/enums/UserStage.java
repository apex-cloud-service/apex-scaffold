package com.apex.scaffold.domain.enums;

/**
 * 用户投票阶段枚举。
 *
 * <p>
 * 根据用户的累计投票次数将用户分为新人期和成熟期两个阶段，
 * 不同阶段采用不同的分差策略及推荐规则。
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum UserStage {

    /**
     * 新人期：投票次数 ≤ 30 次.
     * <ul>
     *   <li>分差范围：12% ~ 30%</li>
     *   <li>前 3 次投票必含头部照片</li>
     * </ul>
     */
    NEWBIE,

    /**
     * 成熟期：投票次数 > 30 次.
     * <ul>
     *   <li>按正确率分层</li>
     *   <li>分差范围：10% ~ 30% / 50% / 70%</li>
     * </ul>
     */
    MATURE;

    /**
     * 新人阶段判定阈值（投票次数）。
     *
     * <p>累计投票次数 ≤ 该值判定为 {@link #NEWBIE}，否则判定为 {@link #MATURE}。</p>
     */
    public static final int NEWBIE_THRESHOLD_COUNT = 30;

    /**
     * 根据投票次数获取对应的用户投票阶段.
     *
     * @param count 用户累计投票次数
     * @return 对应的用户阶段枚举
     */
    public static UserStage fromVoteCount(int count) {
        if (count <= NEWBIE_THRESHOLD_COUNT) {
            return NEWBIE;
        }
        return MATURE;
    }
}
