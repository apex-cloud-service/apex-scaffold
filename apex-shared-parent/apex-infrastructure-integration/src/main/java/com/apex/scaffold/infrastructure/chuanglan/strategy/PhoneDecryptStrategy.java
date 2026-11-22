package com.apex.scaffold.infrastructure.chuanglan.strategy;

/**
 * 手机号解密策略接口（策略模式）。
 *
 * <p>创蓝闪验允许不同平台/应用配置不同的手机号加密方式。本接口定义解密契约，
 * 由具体算法实现按配置装配，调用方（{@link com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService}）
 * 只依赖接口，不感知算法细节。
 *
 * <p><strong>实现现状</strong></p>
 * <ul>
 *   <li><b>AES</b>（{@code encryptType="0"}）：已实现，见 {@link AesPhoneDecryptStrategy}，
 *       AES/CBC/PKCS5Padding，密钥由 {@code md5(appKey)} 派生</li>
 *   <li><b>RSA</b>（{@code encryptType="1"}）：<strong>尚未实现</strong>。当前装配层只注册
 *       AES 实现；如部署使用 RSA 加密，需新增 {@code PhoneDecryptStrategy} 实现并在装配层注册，
 *       否则解密结果不正确</li>
 * </ul>
 *
 * <p><strong>装配方式</strong></p>
 * <p>实现类由装配层以 Spring Bean 注册（如 auth-service 的 {@code ChuanglanSmsConfig}
 * 注册 {@link AesPhoneDecryptStrategy}），再由 {@code ChuanglanApiService} 构造器注入使用。</p>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><b>策略模式</b>：把解密算法族分别封装为可替换实现</li>
 *   <li>与 Spring 依赖注入结合，由装配层决定注入哪个算法实现</li>
 * </ul>
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 3.1.0
 * @see AesPhoneDecryptStrategy
 * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
 */
public interface PhoneDecryptStrategy {

    /**
     * 解密手机号密文。
     *
     * <p>将创蓝闪验 API 返回的 mobileName 密文解密为明文手机号。
     * 不同加密方式使用不同的算法和密钥派生策略。</p>
     *
     * @param encryptedPhone 手机号密文（16进制字符串）
     * @param appKey         闪验 App Key，用于派生解密密钥
     * @return 解密后的明文手机号
     * @throws com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException 解密失败时抛出
     */
    String decrypt(String encryptedPhone, String appKey);

    /**
     * 返回该策略支持的加密类型标识。
     *
     * <p>与创蓝配置中的 encryptType 字段对应：
     * "0" = AES, "1" = RSA, "default" = 不加密/透传。</p>
     *
     * @return 加密类型标识
     */
    String getEncryptType();
}
