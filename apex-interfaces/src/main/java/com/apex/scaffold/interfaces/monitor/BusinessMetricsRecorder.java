package com.apex.scaffold.interfaces.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 核心业务指标记录器。
 *
 * <p>在核心业务路径成功后递增 Micrometer 计数器，供监控大盘
 * 「核心业务监控」板块与 Prometheus 消费：登录成功、注册成功、文件上传成功。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class BusinessMetricsRecorder {

    /** 登录成功计数器指标名。 */
    private static final String METRIC_LOGIN_SUCCESS = "apex.business.login.success";

    /** 注册成功计数器指标名。 */
    private static final String METRIC_REGISTER = "apex.business.register";

    /** 文件上传成功计数器指标名。 */
    private static final String METRIC_UPLOAD = "apex.business.upload";

    /** Micrometer 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /**
     * 创建业务指标记录器。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public BusinessMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 记录一次登录成功。 */
    public void recordLoginSuccess() {
        counter(METRIC_LOGIN_SUCCESS).increment();
    }

    /** 记录一次注册成功。 */
    public void recordRegister() {
        counter(METRIC_REGISTER).increment();
    }

    /** 记录一次文件上传成功。 */
    public void recordUpload() {
        counter(METRIC_UPLOAD).increment();
    }

    /**
     * 获取（惰性注册）计数器。
     *
     * @param name 指标名
     * @return 计数器
     */
    private Counter counter(String name) {
        return Counter.builder(name).description("核心业务成功次数").register(meterRegistry);
    }
}
