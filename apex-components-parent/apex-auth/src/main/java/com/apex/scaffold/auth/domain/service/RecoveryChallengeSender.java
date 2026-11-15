package com.apex.scaffold.auth.domain.service;

import reactor.core.publisher.Mono;

/**
 * 恢复验证码投递端口。
 *
 * <p>投递目标必须与恢复申请使用同一已验证联系方式；投递失败必须向外传播，
 * 不允许伪造成投递成功，由调用方记录可追踪的审计。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public interface RecoveryChallengeSender {

    /**
     * 向已验证联系方式投递一次性验证码。
     *
     * @param channel 恢复通道（EMAIL / SMS）
     * @param contact 已验证联系方式（明文，仅在投递边界使用，不落库不进日志）
     * @param code    一次性验证码明文（仅在本边界使用，不写日志）
     * @return 投递完成信号（空完成）；投递失败以错误信号向外传播
     */
    Mono<Void> send(String channel, String contact, String code);
}
