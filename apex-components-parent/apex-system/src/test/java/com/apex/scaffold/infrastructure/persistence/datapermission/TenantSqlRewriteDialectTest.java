package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 租户 SQL 改写单元验证（方案 M2 / B7 未验证项收口）。
 *
 * <p>直接验证 {@link DefaultTenantLineHandler} 的租户解析契约——这是 SQL 改写的输入源头：
 * <ul>
 *   <li>有上下文时返回当前租户的字符串字面量（拦截器据此注入 WHERE/INSERT 列）；</li>
 *   <li>上下文缺失时 <b>fail-closed 抛异常</b>（而非回退默认租户或放行全量）；</li>
 *   <li>平台表白名单（菜单/权限码/字典/租户/配置）不参与隔离，业务表参与。</li>
 * </ul>
 *
 * <p>SQL 实际改写的方言无关性由 {@code TenantLineE2eTest}（H2）与真实 MySQL/PG
 * 迁移演练共同保证；本测试聚焦 handler 契约本身，避免重复搭建容器。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("租户 SQL 改写契约测试")
class TenantSqlRewriteDialectTest {

    private final DefaultTenantLineHandler handler = new DefaultTenantLineHandler();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("有租户上下文时返回当前租户字面量")
    void returnsCurrentTenantLiteral() {
        TenantContext.setTenantId("tenant-a");
        assertThat(handler.getTenantId().toString()).isEqualTo("'tenant-a'");

        TenantContext.setTenantId("tenant-b");
        assertThat(handler.getTenantId().toString()).isEqualTo("'tenant-b'");
    }

    @Test
    @DisplayName("租户上下文缺失时 fail-closed 抛异常")
    void failsClosedWithoutTenant() {
        TenantContext.clear();
        assertThatThrownBy(() -> handler.getTenantId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租户上下文未装配");

        TenantContext.setTenantId("  ");
        assertThatThrownBy(() -> handler.getTenantId()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("平台表不参与隔离，业务表参与（白名单语义）")
    void whitelistSemantics() {
        // 平台级定义表：不隔离
        assertThat(handler.ignoreTable("sys_menu")).isTrue();
        assertThat(handler.ignoreTable("sys_permission")).isTrue();
        assertThat(handler.ignoreTable("sys_dict")).isTrue();
        assertThat(handler.ignoreTable("sys_tenant")).isTrue();
        assertThat(handler.ignoreTable("sys_tenant_package")).isTrue();
        assertThat(handler.ignoreTable("sys_config")).isTrue();
        assertThat(handler.ignoreTable("flyway_schema_history")).isTrue();

        // 业务表：参与隔离（含 B2 起租户化的角色表）
        assertThat(handler.ignoreTable("sys_user")).isFalse();
        assertThat(handler.ignoreTable("sys_dept")).isFalse();
        assertThat(handler.ignoreTable("sys_role")).isFalse();
        assertThat(handler.ignoreTable("sys_role_permission")).isFalse();
        assertThat(handler.ignoreTable("sys_tenant_config")).isFalse();
        assertThat(handler.ignoreTable("off_message")).isFalse();
        assertThat(handler.ignoreTable("iot_device")).isFalse();
        assertThat(handler.ignoreTable("int_connector_config")).isFalse();
    }

    @Test
    @DisplayName("表名大小写不敏感（MyBatis-Plus 归一化）")
    void tableNameIsCaseInsensitive() {
        assertThat(handler.ignoreTable("SYS_MENU")).isTrue();
        assertThat(handler.ignoreTable("Sys_Menu")).isTrue();
        assertThat(handler.ignoreTable("SYS_USER")).isFalse();
        assertThat(handler.ignoreTable(null)).isFalse();
    }

    @Test
    @DisplayName("JSqlParser 可解析代表性查询/更新语句（改写前置条件）")
    void representativeStatementsAreParsable() throws Exception {
        // 拦截器以 JSqlParser 解析 SQL 后改写；此处验证代表性语句可解析，
        // 是改写在 MySQL/PG 双方言下均可实施的前置条件。
        for (String sql : new String[] {
            "SELECT id, tenant_id, record_name FROM tenant_probe WHERE deleted = 0",
            "SELECT COUNT(*) FROM tenant_probe",
            "UPDATE tenant_probe SET probe_value = 'x' WHERE id = 1",
            "DELETE FROM tenant_probe WHERE id = 2",
            "INSERT INTO tenant_probe (tenant_id, probe_value) VALUES ('t', 'v')"
        }) {
            Statement statement = CCJSqlParserUtil.parse(sql);
            assertThat(statement).as("应可解析: " + sql).isNotNull();
            if (statement instanceof Select select) {
                assertThat(select.getSelectBody()).isNotNull();
            }
        }
    }
}
