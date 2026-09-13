package com.apex.scaffold.application.service.auth;

/**
 * 一次性认证票据用途。
 *
 * <p>用于区分多步认证流程中的票据类型，确保票据不被跨场景误用。
 * 每种类型的票据由 {@link AuthTicketService} 签发并消费，具有 5 分钟有效期。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum AuthTicketType {
    /** 微信绑定手机号流程。 */
    WECHAT_BIND,
    /** 新用户注册流程。 */
    REGISTRATION
}
