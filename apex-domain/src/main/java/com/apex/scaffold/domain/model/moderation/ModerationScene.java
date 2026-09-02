package com.apex.scaffold.domain.model.moderation;

/**
 * 业务侧需要覆盖的图片审核场景枚举。
 *
 * <p>具体启用项由云端审核策略决定，每个枚举常量对应一个审核场景。</p>
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
/**
 * ModerationScene 组件。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ModerationScene {

    /** 色情内容审核 */
    PORN("porn"),

    /** 暴恐内容审核 */
    TERRORISM("terrorism"),

    /** 广告内容审核 */
    AD("ad"),

    /** 直播内容审核 */
    LIVE("live"),

    /** Logo标识审核 */
    LOGO("logo");

    /** 审核场景编码 */
    private final String code;

    /**
     * 创建 {@link ModerationScene} 实例。
     *
     * @param code code 参数
     */
    ModerationScene(String code) {
        this.code = code;
    }

    /**
     * 获取审核场景的业务编码。
     *
     * @return 业务编码，用于与云端审核策略通信
     */
    public String code() {
        return code;
    }
}
