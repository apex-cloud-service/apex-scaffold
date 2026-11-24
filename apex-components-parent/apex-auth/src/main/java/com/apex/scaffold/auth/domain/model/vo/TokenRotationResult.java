package com.apex.scaffold.auth.domain.model.vo;

/**
 * 同步事务提交后的轮换结果；重放撤销以值返回，避免抛异常使安全撤销回滚。
 *
 * @param status 轮换成功、消费重放或普通拒绝
 * @param tokens 成功时为新持久化记录，重放及拒绝时为 null
 * @author Apex Team
 * @since 1.3.1
 */
public record TokenRotationResult(Status status, TokenEntityPair tokens) {
    /**
     * 轮换结果分类：成功保存新令牌对、检测到消费重放、或普通拒绝。
     *
     * @author Apex Team
     */
    public enum Status {
        /** 旧刷新令牌已消费且新令牌对已提交。 */
        ROTATED,
        /** 已消费刷新令牌被再次使用；同事务撤销已完成。 */
        REPLAY,
        /** 记录不存在、已手工撤销或校验不通过，不签发新令牌。 */
        REJECTED
    }

    /**
     * 构造成功轮换结果。
     *
     * @param tokens 已提交的新令牌记录对
     * @return 状态为 {@link Status#ROTATED} 的结果
     */
    public static TokenRotationResult rotated(TokenEntityPair tokens) {
        return new TokenRotationResult(Status.ROTATED, tokens);
    }

    /**
     * 构造消费重放结果。
     *
     * @return 状态为 {@link Status#REPLAY} 且不含新令牌的结果
     */
    public static TokenRotationResult replay() {
        return new TokenRotationResult(Status.REPLAY, null);
    }

    /**
     * 构造普通拒绝结果。
     *
     * @return 状态为 {@link Status#REJECTED} 且不含新令牌的结果
     */
    public static TokenRotationResult rejected() {
        return new TokenRotationResult(Status.REJECTED, null);
    }
}
