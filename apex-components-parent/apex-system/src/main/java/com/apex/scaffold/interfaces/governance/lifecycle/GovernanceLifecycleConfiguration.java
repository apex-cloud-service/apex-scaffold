package com.apex.scaffold.interfaces.governance.lifecycle;

import com.apex.scaffold.infrastructure.mq.MqDrainParticipant;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.interfaces.governance.config.GovernanceProperties;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategyRegistry;
import com.apex.scaffold.interfaces.governance.platform.DeploymentMode;
import com.apex.scaffold.interfaces.governance.platform.DeploymentModeResolver;
import com.apex.scaffold.interfaces.governance.platform.compose.ComposeGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.kubernetes.KubernetesGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.standalone.StandaloneGovernanceStrategy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 服务治理核心 Spring 装配配置。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "apex.governance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GovernanceLifecycleConfiguration {
    /**
     * 创建启动期部署模式解析器。
     *
     * @return 部署模式解析器
     */
    @Bean
    public DeploymentModeResolver deploymentModeResolver() {
        return new DeploymentModeResolver();
    }

    /**
     * 解析启动实例的最终部署模式。
     *
     * @param resolver 模式解析器
     * @param properties 治理配置
     * @return 最终部署模式
     */
    @Bean
    public DeploymentMode deploymentMode(DeploymentModeResolver resolver, GovernanceProperties properties) {
        return resolver.resolve(properties, System.getenv());
    }

    /**
     * 创建 standalone 治理策略。
     *
     * @param readiness readiness 控制器
     * @return standalone 策略
     */
    @Bean
    public DeploymentGovernanceStrategy standaloneGovernanceStrategy(ReadinessStateController readiness) {
        return new StandaloneGovernanceStrategy(readiness);
    }

    /**
     * 创建 Compose 治理策略。
     *
     * @param readiness readiness 控制器
     * @return Compose 策略
     */
    @Bean
    public DeploymentGovernanceStrategy composeGovernanceStrategy(ReadinessStateController readiness) {
        return new ComposeGovernanceStrategy(readiness);
    }

    /**
     * 创建 Kubernetes 治理策略。
     *
     * @param readiness readiness 控制器
     * @return Kubernetes 策略
     */
    @Bean
    public DeploymentGovernanceStrategy kubernetesGovernanceStrategy(ReadinessStateController readiness) {
        return new KubernetesGovernanceStrategy(readiness);
    }

    /**
     * 创建策略注册表。
     *
     * @param strategies 所有治理策略
     * @param mode 最终部署模式
     * @return 策略注册表
     */
    @Bean
    public DeploymentGovernanceStrategyRegistry governanceStrategyRegistry(
            List<DeploymentGovernanceStrategy> strategies, DeploymentMode mode) {
        return new DeploymentGovernanceStrategyRegistry(strategies, mode);
    }

    /**
     * 创建资源排空注册表。
     *
     * @return 资源排空注册表
     */
    @Bean
    public ResourceDrainRegistry resourceDrainRegistry() {
        return new ResourceDrainRegistry();
    }

    /**
     * 在应用就绪后按最终可用的 MQ 组件注册资源参与者。
     *
     * <p>组件扫描阶段可见的定义可能随后被条件装配排除，因此在容器刷新完成后查询实际依赖，
     * 避免未配置 RocketMQ 时由治理配置引入强制依赖。重复就绪事件复用已有参与者。
     *
     * @param event 应用就绪事件
     */
    @EventListener
    public void registerMqParticipant(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        ResourceDrainRegistry drainRegistry = context.getBean(ResourceDrainRegistry.class);
        context.getBeansOfType(MqDrainParticipant.class).values().forEach(drainRegistry::register);
        if (drainRegistry.participants().stream().anyMatch(MqDrainParticipant.class::isInstance)) {
            return;
        }
        MqConsumerService consumer =
                context.getBeanProvider(MqConsumerService.class).getIfAvailable();
        DeadLetterQueueProcessor deadLetter =
                context.getBeanProvider(DeadLetterQueueProcessor.class).getIfAvailable();
        if (consumer != null && deadLetter != null) {
            drainRegistry.register(new MqDrainParticipant(consumer, deadLetter));
        }
    }

    /**
     * 创建生命周期状态机。
     *
     * @return 生命周期状态机
     */
    @Bean
    public ServiceLifecycleStateMachine serviceLifecycleStateMachine() {
        return new ServiceLifecycleStateMachine();
    }

    /**
     * 创建统一关停协调器。
     *
     * @param properties 治理配置
     * @param registry 策略注册表
     * @param stateMachine 生命周期状态机
     * @param readiness readiness 控制器
     * @param drainRegistry 资源注册表
     * @return 关停协调器
     */
    @Bean
    public GracefulShutdownCoordinator gracefulShutdownCoordinator(
            GovernanceProperties properties,
            DeploymentGovernanceStrategyRegistry registry,
            ServiceLifecycleStateMachine stateMachine,
            ReadinessStateController readiness,
            ResourceDrainRegistry drainRegistry) {
        return new GracefulShutdownCoordinator(
                properties, registry.activeStrategy(), stateMachine, readiness, drainRegistry);
    }
}
