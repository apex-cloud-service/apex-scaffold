package com.apex.scaffold.domain.service;

import reactor.core.publisher.Mono;

/**
 * 短信验证码服务接口
 *
 * <p>定义短信验证码的发送和验证规范。用于短信验证码登录场景，
 * 当用户在一键登录时选择"使用其他手机号"，系统通过此服务
 * 发送验证码到用户手机并进行验证。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>发送验证码：生成随机 6 位数字验证码并存储到缓存</li>
 *   <li>验证验证码：校验用户输入的验证码是否与缓存匹配</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SmsCodeService {

    /**
     * 发送验证码并存储
     *
     * <p>生成 6 位随机数字验证码，存储到内存缓存中并设置 5 分钟有效期，
     * 同时输出到日志（实际生产环境应对接短信网关发送真实短信）。
     *
     * @param phone 目标手机号
     * @return 空 Mono，表示发送完成
     */
    Mono<Void> sendAndStore(String phone);

    /**
     * 验证短信验证码
     *
     * <p>校验用户输入的验证码是否与缓存中的验证码匹配。
     * 验证成功后自动从缓存中移除该验证码（一次性使用）。
     *
     * @param phone 手机号
     * @param code 用户输入的验证码
     * @return true 表示验证通过，false 表示验证码错误或已过期
     */
    Mono<Boolean> validate(String phone, String code);
}
