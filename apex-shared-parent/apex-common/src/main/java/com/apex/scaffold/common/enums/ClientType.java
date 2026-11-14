package com.apex.scaffold.common.enums;

import org.springframework.lang.Nullable;

/**
 * 客户端类型枚举
 *
 * <p>多端统一接入的终端标识，客户端通过请求头 {@code X-Client-Type} 上报
 * （登录接口亦可由 {@code LoginRequest.clientType} 兜底），服务端用于：
 *
 * <ul>
 *   <li>JWT claims 记录 {@code clientType}，会话可按终端维度审计与追踪</li>
 *   <li>登录日志 {@code sys_login_log.client_type} 落库</li>
 *   <li>后续按端会话策略（如 PC 唯一、App 多设备并存）的扩展依据</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>存量客户端不携带 {@code X-Client-Type} 时按 {@link #UNKNOWN} 处理，
 * 行为与改造前保持一致；无法识别的取值同样归入 {@link #UNKNOWN}，
 * 不产生错误，保证多端灰度期间平滑过渡。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ClientType {

    /** PC 管理后台（认证中心独立登录链路）。 */
    PC_ADMIN,

    /** PC Web 端（C 端网页）。 */
    PC_WEB,

    /** 微信公众号 H5。 */
    WECHAT_MP,

    /** 微信小程序。 */
    MINI_PROGRAM,

    /** Android App。 */
    APP_ANDROID,

    /** iOS App。 */
    APP_IOS,

    /** 其他 H5 页面。 */
    H5,

    /** 未知/未上报：存量客户端或缺省兜底。 */
    UNKNOWN;

    /** 请求头名称：客户端类型。 */
    public static final String HEADER = "X-Client-Type";

    /**
     * 客户端类型编码解析。
     *
     * @param code 类型编码（不区分大小写），允许为 {@code null}
     * @return 对应类型；编码为空或无法识别时返回 {@link #UNKNOWN}
     */
    public static ClientType fromCode(@Nullable String code) {
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
