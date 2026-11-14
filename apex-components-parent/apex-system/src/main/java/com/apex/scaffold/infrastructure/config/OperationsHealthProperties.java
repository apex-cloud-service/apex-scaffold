package com.apex.scaffold.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运维体检快照增强配置。
 *
 * <p>承载 O07b 增强部分的四项参数：定时留存、保留期清理、签名防篡改与异步导出。
 * 所有数值在下发 setter 中做下限兜底，避免运维误配成 0 或负数导致任务空转或无限膨胀。</p>
 *
 * <p><b>位置说明</b>：本类被基础设施层的签名密钥适配器与异步导出调度器直接读取，
 * 放在 {@code infrastructure.config} 是为了满足「基础设施层不得依赖接口层」的架构约束；
 * 应用层通过 {@code HealthExportSettingsPort} 端口读取，不直接引用本类。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.2
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.operations.health")
public class OperationsHealthProperties {

    /** 默认保留天数：90 天。 */
    public static final int DEFAULT_RETENTION_DAYS = 90;

    /** 默认清理单批条数。 */
    public static final int DEFAULT_PURGE_BATCH_SIZE = 500;

    /** 默认异步导出轮询间隔（毫秒）。 */
    public static final long DEFAULT_EXPORT_POLL_DELAY_MS = 5000L;

    /** 异步导出单次最大生成行数。 */
    public static final int DEFAULT_EXPORT_MAX_ROWS = 200000;

    /** 异步导出结果保留小时数：24 小时。 */
    public static final int DEFAULT_EXPORT_RESULT_TTL_HOURS = 24;

    /** 定时留存配置。 */
    private final Schedule schedule = new Schedule();

    /** 保留期清理配置。 */
    private final Retention retention = new Retention();

    /** 签名防篡改配置。 */
    private final Signature signature = new Signature();

    /** 异步导出配置。 */
    private final AsyncExport asyncExport = new AsyncExport();

    /** 定时留存配置。 */
    @Getter
    @Setter
    public static class Schedule {

        /** 是否启用定时留存。 */
        private boolean enabled = true;

        /** 快照归属租户标识；定时任务无请求上下文，需使用平台租户。 */
        private String tenantId = "default";

        /**
         * 采集 cron 表达式。
         *
         * <p>默认每小时整点采集一次；单实例采集结果不代表集群，
         * 多实例部署时会各自落一条快照，由趋势分析按实例区分。</p>
         */
        private String cron = "0 0 * * * ?";
    }

    /** 保留期清理配置。 */
    @Getter
    @Setter
    public static class Retention {

        /** 是否启用保留期清理。 */
        private boolean enabled = true;

        /** 保留天数，小于 1 时回落到默认值。 */
        private int days = DEFAULT_RETENTION_DAYS;

        /** 单批删除条数，限制单次事务规模。 */
        private int batchSize = DEFAULT_PURGE_BATCH_SIZE;

        /** 清理 cron 表达式，默认每天 03:30。 */
        private String cron = "0 30 3 * * ?";

        /** 单次清理最多执行的批次数，避免长时间占用连接。 */
        private int maxBatches = 20;

        public void setDays(int days) {
            this.days = days < 1 ? DEFAULT_RETENTION_DAYS : days;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize < 1 ? DEFAULT_PURGE_BATCH_SIZE : batchSize;
        }

        public void setMaxBatches(int maxBatches) {
            this.maxBatches = maxBatches < 1 ? 20 : maxBatches;
        }
    }

    /** 签名防篡改配置。 */
    @Getter
    @Setter
    public static class Signature {

        /**
         * 签名密钥。
         *
         * <p>缺省复用 {@code apex.security.data-encryption-key}：该密钥已要求跨实例、
         * 跨重启稳定保存，且已在部署校验中强制检查，无需额外维护第二份密钥。
         * 显式配置本项可覆盖。</p>
         */
        private String secret = "";

        /** 是否启用签名校验；关闭后列表中的完整性一律为 UNVERIFIABLE。 */
        private boolean enabled = true;
    }

    /** 异步导出配置。 */
    @Getter
    @Setter
    public static class AsyncExport {

        /** 是否启用异步导出轮询。 */
        private boolean enabled = true;

        /** 轮询间隔（毫秒）。 */
        private long pollDelayMs = DEFAULT_EXPORT_POLL_DELAY_MS;

        /** 异步导出任务归属租户标识；定时任务无请求上下文，需显式指定。 */
        private String tenantId = "default";

        /** 单任务最大生成行数。 */
        private int maxRows = DEFAULT_EXPORT_MAX_ROWS;

        /** 结果保留小时数，超时任务与文件一起清理。 */
        private int resultTtlHours = DEFAULT_EXPORT_RESULT_TTL_HOURS;

        /** 单任务最大尝试次数，超过后标记 FAILED。 */
        private int maxAttempts = 3;

        public void setPollDelayMs(long pollDelayMs) {
            this.pollDelayMs = pollDelayMs < 1000L ? DEFAULT_EXPORT_POLL_DELAY_MS : pollDelayMs;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows < 1 ? DEFAULT_EXPORT_MAX_ROWS : maxRows;
        }

        public void setResultTtlHours(int resultTtlHours) {
            this.resultTtlHours = resultTtlHours < 1 ? DEFAULT_EXPORT_RESULT_TTL_HOURS : resultTtlHours;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts < 1 ? 3 : maxAttempts;
        }
    }
}
