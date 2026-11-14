package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ProductionDatabaseSafetyValidator} 单元测试。
 *
 * @author Apex Team
 * @since 3.2.0
 */
@DisplayName("生产数据库持久化参数校验")
class ProductionDatabaseSafetyValidatorTest {

    /** redo 与 binlog 都逐事务刷盘时允许启动。 */
    @Test
    void shouldAcceptFullyDurableSettings() {
        ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot snapshot =
                new ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot(1, 1, true);

        assertThatCode(() -> ProductionDatabaseSafetyValidator.validate(snapshot))
                .doesNotThrowAnyException();
    }

    /** 未启用 binlog 时不约束 sync_binlog，但仍要求 redo 逐事务刷盘。 */
    @Test
    void shouldIgnoreSyncBinlogWhenBinaryLogIsDisabled() {
        ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot snapshot =
                new ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot(1, 1000, false);

        assertThatCode(() -> ProductionDatabaseSafetyValidator.validate(snapshot))
                .doesNotThrowAnyException();
    }

    /** 拒绝事务提交时不立即刷新 redo 的配置。 */
    @Test
    void shouldRejectRelaxedRedoDurability() {
        ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot snapshot =
                new ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot(2, 1, true);

        assertThatThrownBy(() -> ProductionDatabaseSafetyValidator.validate(snapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("innodb_flush_log_at_trx_commit")
                .hasMessageContaining("当前为 2");
    }

    /** 启用 binlog 时拒绝批量刷新配置。 */
    @Test
    void shouldRejectRelaxedBinlogDurability() {
        ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot snapshot =
                new ProductionDatabaseSafetyValidator.DatabaseDurabilitySnapshot(1, 1000, true);

        assertThatThrownBy(() -> ProductionDatabaseSafetyValidator.validate(snapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sync_binlog")
                .hasMessageContaining("当前为 1000");
    }
}
