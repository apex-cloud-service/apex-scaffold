package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.domain.service.SmsCodePurpose;
import com.apex.scaffold.domain.service.SmsCodePurposeService;
import com.apex.scaffold.domain.service.SmsCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 默认短信验证码服务适配器。
 *
 * <p>生产认证入口和创蓝专用入口必须共享同一个真实短信发送能力，避免历史实现只把
 * 验证码写入 Redis 和日志、却没有向用户发送短信。该适配器保留
 * {@code defaultSmsCodeService} Bean 名称以兼容既有登录策略，并将发送与核销统一委托给
 * {@code chuanglanSmsCodeService}。</p>
 *
 * <p>适配器不生成、不缓存也不记录验证码，验证码的安全随机生成、供应商发送、原子核销
 * 和失败清理由被委托服务集中负责。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Service("defaultSmsCodeService")
public class SmsCodeServiceImpl implements SmsCodeService, SmsCodePurposeService {
    private static final Logger log = LoggerFactory.getLogger(SmsCodeServiceImpl.class);

    /** 真实短信验证码服务。 */
    private final SmsCodeService delegate;

    /**
     * 创建默认短信验证码适配器。
     *
     * @param delegate 创蓝短信验证码服务
     */
    public SmsCodeServiceImpl(@Qualifier("chuanglanSmsCodeService") SmsCodeService delegate) {
        this.delegate = delegate;
    }

    /**
     * 通过真实短信供应商发送并保存验证码。
     *
     * @param phone 目标手机号
     * @return 发送完成信号
     */
    @Override
    public Mono<Void> sendAndStore(String phone) {
        log.debug("默认短信入口委托到生产短信供应商");
        return delegate.sendAndStore(phone);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> sendAndStore(String phone, SmsCodePurpose purpose) {
        if (delegate instanceof SmsCodePurposeService purposeService) {
            return purposeService.sendAndStore(phone, purpose);
        }
        return delegate.sendAndStore(phone);
    }

    /**
     * 原子校验并核销验证码。
     *
     * @param phone 手机号
     * @param code 用户提交的验证码
     * @return 验证结果
     */
    @Override
    public Mono<Boolean> validate(String phone, String code) {
        return delegate.validate(phone, code);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> validate(String phone, String code, SmsCodePurpose purpose) {
        if (delegate instanceof SmsCodePurposeService purposeService) {
            return purposeService.validate(phone, code, purpose);
        }
        return delegate.validate(phone, code);
    }
}
