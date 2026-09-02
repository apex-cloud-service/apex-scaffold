package com.apex.scaffold.application.service.auth;

/**
 * 客户端在认证流程中应执行的下一步。
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
/**
 * AuthNextAction 组件。
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
