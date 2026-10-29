package com.apex.scaffold.seata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seata 默认关闭；支持通过 mode 选择 AT/TCC/SAGA/XA 事务模式。
 *
 * <p>内部传播仅允许显式配置的路径、目标和独立凭据；四种模式共用同一传播与 Outbox 防护约束。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties("apex.seata")
public class SeataProperties {
    /** Seata 事务模式：AT 为默认，分支类型决定数据源代理与 undo_log 需求。 */
    public enum Mode {
        /** 自动补偿：需要 undo_log 表，数据源必须为单 JDBC。 */
        AT,
        /** 两阶段补偿：业务自实现 Try/Confirm/Cancel，无 undo_log；由 TCC 扫描器代理 @LocalTCC 动作。 */
        TCC,
        /** 状态机编排：需要 SAGA 存储三表与状态机 JSON 定义。 */
        SAGA,
        /** 强隔离 XA：依赖数据库 XA 驱动，无 undo_log。 */
        XA
    }

    private boolean enabled;
    /** 事务模式，缺省 AT，保持既有行为兼容。 */
    private Mode mode = Mode.AT;

    private String applicationId;
    private String transactionServiceGroup;
    private int timeoutMillis = 30000;
    private String inboundPathPrefix = "/internal/seata/";
    private String propagationSecret;
    private List<String> allowedOrigins = new ArrayList<>();
    /** SAGA 模式专属配置；其他模式忽略。 */
    private final Saga saga = new Saga();

    /**
     * SAGA 状态机引擎配置。
     */
    @Getter
    @Setter
    public static class Saga {
        /** 状态机定义 JSON 的 classpath 资源模式；空数组表示仅装配引擎、定义后续通过 API 注册。 */
        private String[] stateMachineResources = new String[0];
        /** SAGA 存储表名前缀，须与所属服务执行的 SAGA 建表脚本一致。 */
        private String tablePrefix = "seata_";
    }
}
