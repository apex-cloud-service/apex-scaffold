package com.apex.scaffold.gateway.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关访问日志落库配置属性.
 *
 * <p>绑定 {@code apex.gateway.access-log.*}，统一驱动访问日志落库策略
 * （{@link AccessLogService}）。既可经 {@code application.yml} 启动兜底，
 * 也可经 Nacos「服务私有配置」热更新，无需重启网关。
 *
 * <p><b>默认关闭</b>（{@code enabled=false}）：落库依赖外部 MySQL，
 * 为避免在未接入数据库的环境下启动即报错，默认关闭，按需显式开启。
 *
 * <p>典型配置示例：
 * <pre>{@code
 * apex:
 *   gateway:
 *     access-log:
 *       enabled: true
 *       sample-rate: 1.0            # 0.0–1.0，P0 全量，高 QPS 可降采样
 *       max-header-length: 512      # 单个请求头值最大记录长度
 *       persist-request-body: false # 是否记录请求体（默认关闭，防敏感数据外溢）
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@ConfigurationProperties(prefix = "apex.gateway.access-log")
public class AccessLogProperties {

    /** 落库总开关（关闭时访问日志仅打印，不写库） */
    private boolean enabled = false;

    /** 采样率（0.0–1.0）：命中采样的请求才落库，用于高 QPS 场景降载 */
    private double sampleRate = 1.0d;

    /** 单个请求头值的最大记录长度（超出截断，防止超长头膨胀存储） */
    private int maxHeaderLength = 512;

    /** 是否记录请求体（默认关闭；开启存在敏感数据外溢风险） */
    private boolean persistRequestBody = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getMaxHeaderLength() {
        return maxHeaderLength;
    }

    public void setMaxHeaderLength(int maxHeaderLength) {
        this.maxHeaderLength = maxHeaderLength;
    }

    public boolean isPersistRequestBody() {
        return persistRequestBody;
    }

    public void setPersistRequestBody(boolean persistRequestBody) {
        this.persistRequestBody = persistRequestBody;
    }
}
