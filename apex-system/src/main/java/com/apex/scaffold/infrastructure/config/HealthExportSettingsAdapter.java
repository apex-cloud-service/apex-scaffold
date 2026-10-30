package com.apex.scaffold.infrastructure.config;

import com.apex.scaffold.domain.service.HealthExportSettingsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link HealthExportSettingsPort} 的配置适配器。
 *
 * <p>把 {@link OperationsHealthProperties} 中的异步导出参数暴露给应用层与调度层，
 * 避免应用层直接依赖基础设施层配置类。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
@Component
@RequiredArgsConstructor
public class HealthExportSettingsAdapter implements HealthExportSettingsPort {

    /** 分批读取快照的默认页大小。 */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final OperationsHealthProperties properties;

    @Override
    public int maxRows() {
        return properties.getAsyncExport().getMaxRows();
    }

    @Override
    public int maxAttempts() {
        return properties.getAsyncExport().getMaxAttempts();
    }

    @Override
    public int resultTtlHours() {
        return properties.getAsyncExport().getResultTtlHours();
    }

    @Override
    public int batchSize() {
        return DEFAULT_BATCH_SIZE;
    }

    @Override
    public String tenantId() {
        String configured = properties.getAsyncExport().getTenantId();
        return configured == null || configured.isBlank() ? "default" : configured.trim();
    }
}
