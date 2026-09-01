package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.persistence.config.MyMetaObjectHandler;
import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleDeptMapper;
import com.apex.scaffold.infrastructure.persistence.po.DeptPO;
import com.apex.scaffold.infrastructure.persistence.po.RoleDeptPO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("租户行拦截器端到端测试(H2)")
class TenantLineE2eH2Test {

    private static SqlSessionFactory sqlSessionFactory;
    private static HikariDataSource dataSource;

    @BeforeAll
    static void setUpFactory() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:tenantline;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tenant_record ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "tenant_id VARCHAR(64) NOT NULL, "
                    + "record_name VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE sys_dept ("
                    + "id BIGINT PRIMARY KEY, "
                    + "tenant_id VARCHAR(64) NOT NULL, "
                    + "parent_id BIGINT NOT NULL, "
                    + "ancestors VARCHAR(500) NOT NULL, "
                    + "name VARCHAR(64) NOT NULL, "
                    + "order_num INT NOT NULL, "
                    + "leader VARCHAR(64), "
                    + "phone VARCHAR(32), "
                    + "email VARCHAR(128), "
                    + "status INT NOT NULL, "
                    + "create_time TIMESTAMP, "
                    + "update_time TIMESTAMP, "
                    + "version INT NOT NULL DEFAULT 0, "
                    + "deleted INT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE sys_role_dept ("
                    + "id BIGINT PRIMARY KEY, "
                    + "role_id BIGINT NOT NULL, "
                    + "dept_id BIGINT NOT NULL, "
                    + "tenant_id VARCHAR(64) NOT NULL, "
                    + "create_time TIMESTAMP, "
                    + "update_time TIMESTAMP, "
                    + "version INT NOT NULL DEFAULT 0, "
                    + "deleted INT NOT NULL DEFAULT 0)");
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new DefaultTenantLineHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        MybatisConfiguration configuration = new MybatisConfiguration();
        com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig =
                new com.baomidou.mybatisplus.core.config.GlobalConfig();
        globalConfig.setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator());
        globalConfig.setMetaObjectHandler(new MyMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(TenantRecordMapper.class);
        configuration.addMapper(DeptMapper.class);
        configuration.addMapper(RoleDeptMapper.class);

        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetRecords() throws Exception {
        TenantContext.clear();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE tenant_record RESTART IDENTITY");
            statement.execute("TRUNCATE TABLE sys_role_dept");
            statement.execute("TRUNCATE TABLE sys_dept");
            statement.execute("INSERT INTO tenant_record (tenant_id, record_name) VALUES "
                    + "('tenant-a', 'a-record'), ('tenant-b', 'b-record'), ('tenant-b', 'b-delete')");
            statement.execute("INSERT INTO sys_dept "
                    + "(id, tenant_id, parent_id, ancestors, name, order_num, status, deleted) VALUES "
                    + "(100, 'tenant-a', 0, '0', 'a-dept', 1, 1, 0), "
                    + "(200, 'tenant-b', 0, '0', 'b-dept', 1, 1, 0)");
            statement.execute("INSERT INTO sys_role_dept "
                    + "(id, role_id, dept_id, tenant_id, deleted) VALUES "
                    + "(1000, 10, 100, 'tenant-a', 0), "
                    + "(2000, 10, 200, 'tenant-b', 0)");
        }
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @AfterAll
    static void tearDownFactory() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void shouldSelectOnlyCurrentTenantRecords() {
        TenantContext.setTenantId("tenant-b");

        List<TenantRecordPO> records = execute(mapper -> mapper.selectList(null));

        assertThat(records).extracting(TenantRecordPO::getRecordName).containsExactlyInAnyOrder("b-record", "b-delete");
    }

    @Test
    void shouldSelectOnlyCurrentTenantDepartments() {
        TenantContext.setTenantId("tenant-a");

        List<DeptPO> departments = executeDept(mapper -> mapper.selectList(null));

        assertThat(departments).extracting(DeptPO::getName).containsExactly("a-dept");
        assertThat(departments).extracting(DeptPO::getTenantId).containsExactly("tenant-a");
    }

    @Test
    void shouldSelectOnlyCurrentTenantRoleDepartments() {
        TenantContext.setTenantId("tenant-b");

        List<RoleDeptPO> relations = executeRoleDept(mapper -> mapper.selectList(null));

        assertThat(relations).extracting(RoleDeptPO::getDeptId).containsExactly(200L);
        assertThat(relations).extracting(RoleDeptPO::getTenantId).containsExactly("tenant-b");
    }

    @Test
    void shouldGenerateDepartmentIdAndFillPersistenceFields() throws Exception {
        TenantContext.setTenantId("tenant-a");
        DeptPO department = new DeptPO();
        department.setParentId(0L);
        department.setAncestors("0");
        department.setName("a-created");
        department.setOrderNum(2);
        department.setStatus(1);

        int inserted = executeDept(mapper -> mapper.insert(department));

        assertThat(inserted).isOne();
        assertThat(department.getId()).isNotNull();
        assertThat(readTenantId("sys_dept", department.getId())).isEqualTo("tenant-a");
        DeptPO stored = executeDept(mapper -> mapper.selectById(department.getId()));
        assertThat(stored.getCreateTime()).isNotNull();
        assertThat(stored.getUpdateTime()).isNotNull();
        assertThat(stored.getVersion()).isZero();
        assertThat(stored.getDeleted()).isZero();
    }

    @Test
    void shouldIncrementDepartmentVersionAndRejectStaleUpdate() {
        TenantContext.setTenantId("tenant-a");
        DeptPO current = executeDept(mapper -> mapper.selectById(100L));
        DeptPO stale = executeDept(mapper -> mapper.selectById(100L));

        current.setName("a-current");
        int currentUpdated = executeDept(mapper -> mapper.updateById(current));
        assertThat(currentUpdated).isOne();
        assertThat(current.getVersion()).isOne();

        stale.setName("a-stale");
        int staleUpdated = executeDept(mapper -> mapper.updateById(stale));
        assertThat(staleUpdated).isZero();
        assertThat(executeDept(mapper -> mapper.selectById(100L)).getName()).isEqualTo("a-current");
    }

    @Test
    void shouldInjectTenantIdWhenInsertingRoleDepartment() throws Exception {
        TenantContext.setTenantId("tenant-a");
        RoleDeptPO relation = new RoleDeptPO();
        relation.setId(1001L);
        relation.setRoleId(11L);
        relation.setDeptId(100L);
        relation.setVersion(0);

        int inserted = executeRoleDept(mapper -> mapper.insert(relation));

        assertThat(inserted).isOne();
        assertThat(readTenantId("sys_role_dept", relation.getId())).isEqualTo("tenant-a");
    }

    @Test
    void shouldInjectTenantIdWhenInserting() throws Exception {
        TenantContext.setTenantId("tenant-a");
        TenantRecordPO record = new TenantRecordPO();
        record.setRecordName("a-inserted");

        int inserted = execute(mapper -> mapper.insert(record));

        assertThat(inserted).isOne();
        assertThat(readTenantId("tenant_record", record.getId())).isEqualTo("tenant-a");
    }

    @Test
    void shouldRejectCrossTenantUpdateAndKeepRecordUnchanged() {
        TenantContext.setTenantId("tenant-a");
        TenantRecordPO record = new TenantRecordPO();
        record.setId(2L);
        record.setRecordName("changed");

        int updated = execute(mapper -> mapper.updateById(record));

        assertThat(updated).isZero();
        TenantContext.setTenantId("tenant-b");
        assertThat(execute(mapper -> mapper.selectById(2L)).getRecordName()).isEqualTo("b-record");
    }

    @Test
    void shouldRejectCrossTenantDeleteAndKeepRecordUnchanged() {
        TenantContext.setTenantId("tenant-a");

        int deleted = execute(mapper -> mapper.deleteById(3L));

        assertThat(deleted).isZero();
        TenantContext.setTenantId("tenant-b");
        assertThat(execute(mapper -> mapper.selectById(3L)).getRecordName()).isEqualTo("b-delete");
    }

    @Test
    void shouldRejectTenantSqlWithoutContext() {
        assertThatThrownBy(() -> execute(mapper -> mapper.selectList(null)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("租户上下文未装配");
    }

    private static String readTenantId(String tableName, Long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT tenant_id FROM " + tableName + " WHERE id = " + id)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static <T> T execute(MapperOperation<T> operation) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return operation.execute(session.getMapper(TenantRecordMapper.class));
        }
    }

    private static <T> T executeDept(DeptMapperOperation<T> operation) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return operation.execute(session.getMapper(DeptMapper.class));
        }
    }

    private static <T> T executeRoleDept(RoleDeptMapperOperation<T> operation) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return operation.execute(session.getMapper(RoleDeptMapper.class));
        }
    }

    @FunctionalInterface
    interface MapperOperation<T> {
        T execute(TenantRecordMapper mapper);
    }

    @FunctionalInterface
    interface DeptMapperOperation<T> {
        T execute(DeptMapper mapper);
    }

    @FunctionalInterface
    interface RoleDeptMapperOperation<T> {
        T execute(RoleDeptMapper mapper);
    }

    @Data
    @TableName("tenant_record")
    static class TenantRecordPO {

        @TableId(type = IdType.AUTO)
        private Long id;

        @TableField("tenant_id")
        private String tenantId;

        @TableField("record_name")
        private String recordName;
    }

    interface TenantRecordMapper extends BaseMapper<TenantRecordPO> {}
}
