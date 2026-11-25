package com.apex.scaffold.job;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册真实 XXL Spring 执行器，自动发现业务服务的 @XxlJob 方法。
 * JAR 级裁剪通过类名字符串守卫可选依赖，避免自动配置加载期缺类。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@AutoConfiguration
@EnableConfigurationProperties(JobExecutorProperties.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
        name = "com.xxl.job.core.executor.impl.XxlJobSpringExecutor")
@ConditionalOnProperty(prefix = "apex.job.executor", name = "enabled", havingValue = "true")
public class JobExecutorAutoConfiguration {
    private static final String ADDRESS_SEPARATOR = ",";
    private static final int MAX_PORT = 65535;
    /** 可选固定任务桥接；业务端口按固定类型获取，不开放 bean 名称配置。 */
    @Bean
    @ConditionalOnProperty(prefix = "apex.job.system", name = "job-id")
    public SystemJobBridge systemJobBridge(
            SystemJobPort port,
            @org.springframework.beans.factory.annotation.Value("${apex.job.system.tenant-id}") String tenant,
            @org.springframework.beans.factory.annotation.Value("${apex.job.system.job-id}") Long jobId) {
        return new SystemJobBridge(port, tenant, jobId);
    }

    /**
     * 注册工作流固定超时任务桥接器。
     *
     * <p>工作流仅在显式选择 XXL 调度（{@code apex.job.workflow.enabled=true}）时注册固定超时入口。
     *
     * @param port 工作流超时任务端口
     * @return 工作流任务桥接器
     */
    @Bean
    @ConditionalOnProperty(prefix = "apex.job.workflow", name = "enabled", havingValue = "true")
    public WorkflowJobBridge workflowJobBridge(WorkflowJobPort port) {
        return new WorkflowJobBridge(port);
    }

    /**
     * 创建 XXL 执行器并校验启动配置。
     *
     * <p>仅显式启用后启动通信端口；缺失应用名、中心地址或至少 32 字符通信令牌时拒绝启动，
     * 中心地址必须为不含凭据的 HTTPS 地址，端口需在合法范围内。
     *
     * @param properties XXL 执行器配置
     * @return 完成配置的 XXL 执行器
     * @throws IllegalArgumentException 配置缺失或非法时抛出
     */
    @Bean
    @ConditionalOnMissingBean
    public XxlJobSpringExecutor xxlJobExecutor(JobExecutorProperties properties) {
        if (properties.getAccessToken() == null
                || properties.getAccessToken().length() < 32
                || properties.getAppName() == null
                || properties.getAppName().isBlank()
                || properties.getAdminAddresses() == null
                || properties.getAdminAddresses().isBlank()) {
            throw new IllegalArgumentException("XXL 执行器需要应用名、中心地址与至少32字符通信令牌");
        }
        for (String address : properties.getAdminAddresses().split(ADDRESS_SEPARATOR)) {
            URI uri = URI.create(address.trim());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("XXL 中心地址必须为不含凭据的 HTTPS 地址");
            }
        }
        if (properties.getPort() < 1 || properties.getPort() > MAX_PORT) {
            throw new IllegalArgumentException("XXL 执行器端口超出范围");
        }
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getAppName());
        executor.setAddress(properties.getAddress());
        executor.setIp(properties.getIp());
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        return executor;
    }
}
