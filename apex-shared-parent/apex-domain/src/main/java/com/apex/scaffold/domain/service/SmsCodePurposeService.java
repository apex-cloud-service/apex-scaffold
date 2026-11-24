package com.apex.scaffold.domain.service;

import reactor.core.publisher.Mono;

/**
 * 支持业务用途隔离的短信验证码服务。
 *
 * <p>这是对历史 {@link SmsCodeService} 的扩展端口。保留旧端口的两个方法，
 * 以兼容现有登录策略和已有适配器。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SmsCodePurposeService {

    /**
     * 发送并存储短信验证码。
     *
     * @param phone 手机号
     * @param purpose 业务用途
     * @return 验证码发送与存储完成信号
     */
    Mono<Void> sendAndStore(String phone, SmsCodePurpose purpose);

    /**
     * 验证短信验证码。
     *
     * @param phone 手机号
     * @param code 验证码
     * @param purpose 业务用途
     * @return 验证是否通过
     */
    Mono<Boolean> validate(String phone, String code, SmsCodePurpose purpose);
}
