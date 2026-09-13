package com.apex.scaffold.application.service.auth;

/**
 * 客户端在认证流程中应执行的下一步。
 *
 * <p>用于指导前端在登录响应后执行相应的后续操作，实现多步认证流程的状态机控制。
 * 认证服务根据用户当前状态（已注册/未注册/已绑定手机/未绑定手机）返回对应的 Action。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum AuthNextAction {
    /** 用户已完成认证，可直接进入应用。 */
    AUTHENTICATED,
    /** 用户需要完成注册流程。 */
    REGISTER_REQUIRED,
    /** 用户需要绑定手机号。 */
    PHONE_BIND_REQUIRED
}
