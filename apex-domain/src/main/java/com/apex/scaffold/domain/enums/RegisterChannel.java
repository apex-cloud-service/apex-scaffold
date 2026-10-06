package com.apex.scaffold.domain.enums;

/**
 * 注册来源枚举
 *
 * <p>定义 {@code sys_user.register_channel} 的取值，标记账户首次注册时使用的
 * 客户端渠道，用于多端运营统计与账户来源追溯。注册完成后不再随登录终端变化；
 * 登录终端维度由登录日志的 {@code client_type} 字段承载。
 *
 * <ul>
 *   <li>{@link #UNKNOWN} - 未知来源（历史存量数据或缺省兜底）</li>
 *   <li>{@link #SYSTEM} - 系统内置（如管理员种子账户）</li>
 *   <li>{@link #PC_WEB} - PC Web 端（账号密码注册）</li>
 *   <li>{@link #PC_ADMIN} - PC 管理后台（认证中心账户映射生成）</li>
 *   <li>{@link #APP_ANDROID} - Android App</li>
 *   <li>{@link #APP_IOS} - iOS App</li>
 *   <li>{@link #APP_HARMONY} - 鸿蒙 App</li>
 *   <li>{@link #MINI_PROGRAM} - 微信小程序</li>
 *   <li>{@link #WECHAT_MP} - 微信公众号 H5</li>
 *   <li>{@link #H5} - 其他 H5 页面</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum RegisterChannel {

    /** 未知来源：历史存量数据或缺省兜底，行为与改造前保持一致。 */
    UNKNOWN,

    /** 系统内置账户：管理员种子数据、系统任务创建的账户。 */
    SYSTEM,

    /** PC Web 端：账号密码注册场景。 */
    PC_WEB,

    /** PC 管理后台：认证中心账户映射生成的主应用账户。 */
    PC_ADMIN,

    /** Android App。 */
    APP_ANDROID,

    /** iOS App。 */
    APP_IOS,

    /** 鸿蒙 App。 */
    APP_HARMONY,

    /** 微信小程序。 */
    MINI_PROGRAM,

    /** 微信公众号 H5。 */
    WECHAT_MP,

    /** 其他 H5 页面。 */
    H5;

    /**
     * 来源编码解析。
     *
     * @param code 来源编码（不区分大小写），允许为 {@code null}
     * @return 对应来源；编码为空或无法识别时返回 {@link #UNKNOWN}
     */
    public static RegisterChannel fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
