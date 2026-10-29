package com.apex.scaffold.license;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License配置属性
 *
 * @author Apex Team
 * @version 2.2.0
 * @since 1.3.1
 */
@Data
@ConfigurationProperties(prefix = "apex.license")
public class LicenseProperties {

    /**
     * 是否启用License验证。
     *
     * <p><b>注意</b>：该开关仅允许"更严格"方向——
     * 设为 {@code false} 并不能绕过商业功能校验，而是强制进入降级模式
     * （全部商业功能拒绝、@FeatureGate 切面仍然生效）。
     * License 校验组件本身始终随包装配，不可整体关闭。</p>
     */
    private boolean enabled = true;

    /**
     * License文件路径（支持 classpath: 前缀与文件系统路径）
     */
    private String filePath = "classpath:license/license-basic.json";

    /**
     * 公钥(Base64编码)
     */
    private String publicKey;

    /**
     * 公钥文件路径(优先级高于publicKey，支持 classpath: 前缀)
     */
    private String publicKeyFile = "classpath:license/public.key";

    /**
     * 运行期重检间隔（默认 1 小时）。
     *
     * <p>重检任务会重新加载并验证 License 文件：
     * <ul>
     *   <li>License 过期 → 运行期即时降级，商业功能拒绝</li>
     *   <li>License 续期/更换 → 不重启即生效</li>
     * </ul>
     * 设为 0 或负值可关闭定时重检（过期即时降级仍由每次功能检查兜底）。</p>
     */
    private Duration recheckInterval = Duration.ofHours(1);

    /**
     * 是否跳过机器指纹验证（仅测试/开发用，默认 false）。
     *
     * <p>设为 true 时，LicenseValidator 不采集本机指纹也不做比对，
     * 相当于关闭机器绑定。生产环境不应设为 true。</p>
     *
     * @since 1.2.1
     */
    private boolean machineFingerprintSkip = false;

    /**
     * 手动覆盖机器指纹（容器/K8s 部署用）。
     *
     * <p>非空时跳过自动采集，直接用此值与 License 中的
     * {@code machineFingerprint} 比对。适用于 Docker 容器内
     * MAC 每次重建变化、或 K8s Pod 中硬件信息不可用的场景：
     * 部署方约定一个稳定标识（如节点名 + 命名空间哈希），
     * 在签发和部署时保持一致。</p>
     *
     * @since 1.2.1
     */
    private String machineFingerprintOverride;
}
