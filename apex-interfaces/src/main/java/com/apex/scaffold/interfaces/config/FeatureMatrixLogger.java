package com.apex.scaffold.interfaces.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 商业版本功能矩阵启动日志。
 *
 * <p>应用启动完成后输出当前构建版本启用的功能清单，
 * 便于售前演示与客户核验版本差异。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureMatrixLogger {

    private final FeatureProperties features;

    /** 应用启动完成后打印功能矩阵。 */
    @EventListener(ApplicationReadyEvent.class)
    public void logFeatureMatrix() {
        log.info(
                """
                ============================================================
                Apex Scaffold 功能矩阵
                ------------------------------------------------------------
                JWT 认证      : {}
                RBAC 权限     : {}
                多级缓存      : {}
                消息队列      : {}
                监控告警      : {}
                链路追踪      : {}
                ------------------------------------------------------------
                提示: 功能开关由 apex.features.* 配置驱动，与 License 授权联动。
                ============================================================""",
                features.isJwtAuth(),
                features.isRbac(),
                features.isCache(),
                features.isMessageQueue(),
                features.isMonitoring(),
                features.isTracing());
    }
}
