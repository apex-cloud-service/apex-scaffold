package com.apex.scaffold.job;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 执行器配置；默认关闭，启用时必须提供调度中心地址与独立通信令牌。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties("apex.job.executor")
public class JobExecutorProperties {
    private boolean enabled;
    private String adminAddresses;
    private String accessToken;
    private String appName;
    private String address;
    private String ip;
    private int port = 9999;
    private String logPath = "logs/xxl-job";
    private int logRetentionDays = 30;
}
