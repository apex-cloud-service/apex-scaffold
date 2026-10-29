package com.apex.scaffold.infrastructure.chuanglan.strategy;

/**
 * 手机号解密策略接口（策略模式）。
 *
 * <p>P0-CL-10重构：根据创蓝文档，不同平台/应用可配置不同的手机号加密方式
 * （AES 或 RSA）。本接口定义手机号解密的统一契约，通过策略模式解耦具体的解密算法，
 * 便于后续扩展新的加密方式而无须修改调用方代码。
 *
 * <h3>支持的加密方式</h3>
 * <ul>
 *   <li><b>AES</b>（encryptType="0"）：AES/CBC/PKCS5Padding，密钥由 md5(appKey) 派生</li>
 *   <li><b>RSA</b>（encryptType="1"）：RSA 私钥解密（待实现）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code * // 根据配置的 encryptType 选择策略
 * PhoneDecryptStrategy strategy = "0".equals(config.getEncryptType())
 *     ? new AesPhoneDecryptStrategy()
 *     : new DefaultPhoneDecryptStrategy();
 * String phone = strategy.decrypt(encryptedPhone, appKey);
 * }</pre>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li><b>策略模式（Strategy Pattern）</b>：定义算法族，分别封装，让它们可以互相替换</li>
 *   <li>与 Spring 的依赖注入结合，运行时根据配置选择具体策略实现</li>
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
