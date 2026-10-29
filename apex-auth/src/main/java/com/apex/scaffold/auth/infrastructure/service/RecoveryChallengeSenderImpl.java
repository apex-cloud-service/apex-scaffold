package com.apex.scaffold.auth.infrastructure.service;

import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import com.apex.scaffold.auth.domain.service.RecoveryChallengeSender;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 恢复验证码投递实现。
 *
 * <p>按通道分发：EMAIL 复用共享邮件服务，SMS 复用创蓝短信服务。两个下游均以
 * {@link ObjectProvider} 软依赖获取，未装配时按服务不可用处理，不伪造成投递成功。
 * 投递失败向外传播，由恢复单状态与日志可追踪。</p>
 *
 * <p>短信通道不经过 {@code SmsCodeService}：该服务自带 Redis 验证码存储与发送冷却，
 * 与密码找回自管的一次性验证码（{@code RecoveryCredentialStore}）语义冲突。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryChallengeSenderImpl implements RecoveryChallengeSender {

    private static final String MAIL_SUBJECT = "密码找回验证码";

    /** 创蓝短信模板接口成功返回码。 */
    private static final String SMS_SUCCESS_CODE = "000000";

    /** 短信返回体中表示结果码的字段名。 */
    private static final String SMS_RESULT_CODE_FIELD = "code";

    private final ObjectProvider<MailService> mailServiceProvider;

    private final ObjectProvider<ChuanglanApiService> chuanglanApiServiceProvider;

    @Override
    public Mono<Void> send(String channel, String contact, String code) {
        if (PasswordRecoveryEntity.CHANNEL_SMS.equals(channel)) {
            return sendSms(contact, code);
        }
        if (PasswordRecoveryEntity.CHANNEL_EMAIL.equals(channel)) {
            return sendMail(contact, code);
        }
        return Mono.error(new BusinessException(ResultCode.PARAM_INVALID, "不支持的恢复通道: " + channel));
    }

    /**
     * 通过创蓝短信发送验证码。
     *
     * @param phone 手机号
     * @param code 验证码
     * @return 投递完成信号，失败时以 {@code SMS_SERVICE_ERROR} 终止
     */
    private Mono<Void> sendSms(String phone, String code) {
        ChuanglanApiService chuanglanApiService = chuanglanApiServiceProvider.getIfAvailable();
        if (chuanglanApiService == null) {
            log.error("短信服务未装配，密码找回验证码投递失败: channel=SMS");
            return Mono.error(new BusinessException(ResultCode.SMS_SERVICE_ERROR, "验证码投递服务不可用，请稍后重试或联系管理员"));
        }
        return Mono.defer(() -> chuanglanApiService.sendSms(phone, code))
                .flatMap(response -> {
                    Object resultCode = response == null ? null : response.get(SMS_RESULT_CODE_FIELD);
                    if (resultCode != null && SMS_SUCCESS_CODE.equals(resultCode.toString())) {
                        return Mono.empty();
                    }
                    log.error("短信验证码投递失败，resultCode: {}", resultCode);
                    return Mono.error(new BusinessException(ResultCode.SMS_SERVICE_ERROR, "验证码投递失败，请稍后重试"));
                })
                .onErrorResume(error -> !(error instanceof BusinessException), error -> {
                    log.error("短信验证码投递异常", error);
                    return Mono.error(new BusinessException(ResultCode.SMS_SERVICE_ERROR, "验证码投递失败，请稍后重试"));
                })
                .then();
    }

    /**
     * 通过共享邮件服务发送验证码。
     *
     * @param email 收件邮箱
     * @param code 验证码
     * @return 投递完成信号，失败时以 {@code SMS_SERVICE_ERROR} 终止
     */
    private Mono<Void> sendMail(String email, String code) {
        MailService mailService = mailServiceProvider.getIfAvailable();
        if (mailService == null) {
            log.error("邮件服务未装配，密码找回验证码投递失败: channel=EMAIL");
            return Mono.error(new BusinessException(ResultCode.SMS_SERVICE_ERROR, "验证码投递服务不可用，请稍后重试或联系管理员"));
        }
        return Mono.fromCallable(() -> {
                    mailService.sendSimple(email, MAIL_SUBJECT, "您的密码找回验证码为：" + code + "，10 分钟内有效。若非本人操作，请忽略此邮件。");
                    return Boolean.TRUE;
                })
                .subscribeOn(BlockingIoSchedulers.remote())
                .then();
    }
}
