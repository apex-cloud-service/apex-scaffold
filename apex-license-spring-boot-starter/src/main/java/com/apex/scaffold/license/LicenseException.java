package com.apex.scaffold.license;

/**
 * License授权异常类
 *
 * <p>本异常用于表示License相关的所有错误情况,包括License文件加载失败、签名验证失败、
 * License已过期、功能未授权等场景。作为运行时异常,不强制要求调用方捕获处理。</p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li><b>License加载失败</b>:文件不存在、格式错误、解析失败</li>
 *   <li><b>签名验证失败</b>:公钥不匹配、签名被篡改、证书无效</li>
 *   <li><b>License过期</b>:超过有效期、版本不匹配</li>
 *   <li><b>功能未授权</b>:当前License版本不包含所需功能</li>
 * </ul>
 *
 * <h3>异常传播</h3>
 * <p>在授权检查流程中,异常按以下方式传播:
 * <ul>
 *   <li>加载阶段:由{@link LicenseLoader}抛出,被{@link LicenseValidator}捕获并转为降级模式</li>
 *   <li>运行时检查:由{@link FeatureGateAspect}切面抛出,交给全局异常处理器转换为统一响应格式</li>
 *   <li>条件装配:由{@link LicenseFeatureCondition}静默处理,不会向外传播</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 抛出简单异常
 * if (license.isExpired()) {
 *     throw new LicenseException("License已过期");
 * }
 *
 * // 抛出带原因的异常
 * try {
 *     byte[] content = Files.readAllBytes(licensePath);
 * } catch (IOException e) {
 *     throw new LicenseException("License文件读取失败: " + licensePath, e);
 * }
 *
 * // 验证签名失败
 * if (!verifySignature(license, publicKey)) {
 *     throw new LicenseException("License签名验证失败,文件可能被篡改");
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see LicenseValidator
 * @see LicenseLoader
 * @see FeatureGateAspect
 */
public class LicenseException extends RuntimeException {

    /**
     * 使用错误消息构造License异常
     *
     * @param message 错误消息,描述License校验失败的具体原因
     */
    public LicenseException(String message) {
        super(message);
    }

    /**
     * 使用错误消息与底层原因构造License异常
     *
     * @param message 错误消息,描述License校验失败的具体原因
     * @param cause   底层触发异常,用于保留完整异常堆栈
     */
    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
