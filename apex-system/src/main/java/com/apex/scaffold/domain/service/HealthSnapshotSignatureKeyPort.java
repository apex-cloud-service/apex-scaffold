package com.apex.scaffold.domain.service;

/**
 * 体检快照签名密钥端口。
 *
 * <p>应用层只关心「当前生效密钥是什么」，不关心密钥来自哪个配置项或密钥管理系统。
 * 实现位于基础设施/接口配置层，可在此处叠加占位值拒绝等校验。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface HealthSnapshotSignatureKeyPort {

    /**
     * 获取当前生效的签名密钥。
     *
     * @return 密钥明文；未配置或校验不通过时返回 {@code null}，调用方应降级为 UNVERIFIABLE
     */
    String currentSecret();
}
