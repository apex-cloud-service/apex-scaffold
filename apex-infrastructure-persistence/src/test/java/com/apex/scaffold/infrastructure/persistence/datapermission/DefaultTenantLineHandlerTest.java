package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultTenantLineHandlerTest {

    private final DefaultTenantLineHandler handler = new DefaultTenantLineHandler();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsTenantSqlWhenContextIsMissing() {
        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租户上下文");
    }

    @Test
    void returnsConfiguredTenantColumnAndCurrentTenantValue() {
        TenantContext.setTenantId("tenant-a");

        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
        assertThat(handler.getTenantId()).isInstanceOf(StringValue.class);
        assertThat(((StringValue) handler.getTenantId()).getValue()).isEqualTo("tenant-a");
    }

    @Test
    void rejectsBlankTenantContext() {
        TenantContext.setTenantId(" ");

        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租户上下文");
    }

    @Test
    void ignoresOnlyConfiguredSystemTablesCaseInsensitively() {
        assertThat(handler.ignoreTable("SYS_ROLE")).isTrue();
        assertThat(handler.ignoreTable("sys_config")).isTrue();
        assertThat(handler.ignoreTable("biz_report")).isFalse();
        assertThat(handler.ignoreTable("sys_user")).isFalse();
        assertThat(handler.ignoreTable("sys_job")).isFalse();
        assertThat(handler.ignoreTable("sys_job_log")).isFalse();
        assertThat(handler.ignoreTable(null)).isFalse();
    }
}
