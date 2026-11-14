package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.persistence.datapermission.DefaultTenantLineHandler;
import com.apex.scaffold.infrastructure.persistence.mapper.JobLogMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.JobMapper;
import com.apex.scaffold.infrastructure.persistence.po.JobLogPO;
import com.apex.scaffold.infrastructure.persistence.po.JobPO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
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

@DisplayName("Job/JobLog 仓储租户行隔离端到端测试")
class JobTenantIsolationTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static HikariDataSource dataSource;

    @BeforeAll
    static void setUpFactory() throws Exception {
        // 使用 H2 内存库（MySQL 兼容模式）替代外部 MySQL，保证无数据库环境下测试自包含运行：
        // - DB_CLOSE_DELAY=-1：连接池关闭连接后数据库保留，随 JVM 生命周期存续；
        // - DATABASE_TO_LOWER/CASE_INSENSITIVE_IDENTIFIERS：与 MySQL 大小写行为对齐。
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:job_tenant_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS sys_job_log");
            statement.execute("DROP TABLE IF EXISTS sys_job");
            statement.execute("CREATE TABLE sys_job ("
                    + "id BIGINT NOT NULL PRIMARY KEY, "
                    + "tenant_id VARCHAR(64), "
                    + "job_name VARCHAR(64) NOT NULL, "
                    + "job_group VARCHAR(64) NOT NULL DEFAULT 'DEFAULT', "
                    + "invoke_target VARCHAR(500) NOT NULL, "
                    + "cron_expression VARCHAR(255) NOT NULL, "
                    + "misfire_policy INT NOT NULL DEFAULT 0, "
                    + "concurrent INT NOT NULL DEFAULT 1, "
                    + "status INT NOT NULL DEFAULT 1, "
                    + "remark VARCHAR(500), "
                    + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "version INT NOT NULL DEFAULT 0, "
                    + "deleted INT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE sys_job_log ("
                    + "id BIGINT NOT NULL PRIMARY KEY, "
                    + "tenant_id VARCHAR(64), "
                    + "job_id BIGINT NOT NULL, "
                    + "job_name VARCHAR(64) NOT NULL DEFAULT '', "
                    + "job_group VARCHAR(64) NOT NULL DEFAULT 'DEFAULT', "
                    + "invoke_target VARCHAR(500) NOT NULL DEFAULT '', "
                    + "job_message VARCHAR(500), "
                    + "status INT NOT NULL DEFAULT 0, "
                    + "exception_info TEXT, "
                    + "start_time DATETIME, "
                    + "end_time DATETIME, "
                    + "duration BIGINT, "
                    + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "version INT NOT NULL DEFAULT 0, "
                    + "deleted INT NOT NULL DEFAULT 0)");
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new DefaultTenantLineHandler()));
        interceptor.addInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(JobMapper.class);
        configuration.addMapper(JobLogMapper.class);

        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetRecords() throws Exception {
        TenantContext.clear();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE sys_job");
            statement.execute("TRUNCATE TABLE sys_job_log");
            statement.execute("INSERT INTO sys_job (id, tenant_id, job_name, job_group, invoke_target, "
                    + "cron_expression, status, create_time, update_time, version, deleted) VALUES "
                    + "(1001, 'tenant-a', 'a-job', 'DEFAULT', 'aBean.run', '0 0 1 * * ?', 1, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0), "
                    + "(1002, 'tenant-b', 'b-job', 'DEFAULT', 'bBean.run', '0 0 2 * * ?', 1, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)");
            statement.execute("INSERT INTO sys_job_log (id, tenant_id, job_id, job_name, job_group, "
                    + "invoke_target, status, start_time, create_time, update_time, version, deleted) VALUES "
                    + "(2001, 'tenant-a', 1001, 'a-job', 'DEFAULT', 'aBean.run', 0, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0), "
                    + "(2002, 'tenant-b', 1002, 'b-job', 'DEFAULT', 'bBean.run', 0, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)");
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
    @DisplayName("当前租户只能查询自己的任务")
    void shouldSelectOnlyCurrentTenantJobs() {
        TenantContext.setTenantId("tenant-a");

        List<JobPO> jobs = execute(JobMapper.class, mapper -> mapper.selectList(null));

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getJobName()).isEqualTo("a-job");
    }

    @Test
    @DisplayName("跨租户按ID查询应返回null")
    void shouldNotFindCrossTenantJobById() {
        TenantContext.setTenantId("tenant-a");

        JobPO job = execute(JobMapper.class, mapper -> mapper.selectById(1002L));

        assertThat(job).isNull();
    }

    @Test
    @DisplayName("插入任务应自动注入当前租户")
    void shouldInjectTenantIdOnInsert() {
        TenantContext.setTenantId("tenant-a");
        JobPO job = new JobPO();
        job.setId(1003L);
        job.setJobName("a-new-job");
        job.setJobGroup("DEFAULT");
        job.setInvokeTarget("aBean.run");
        job.setCronExpression("0 0 3 * * ?");
        job.setStatus(1);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        job.setVersion(0);
        job.setDeleted(0);

        int inserted = execute(JobMapper.class, mapper -> mapper.insert(job));

        assertThat(inserted).isOne();
        TenantContext.setTenantId("tenant-b");
        JobPO crossTenant = execute(JobMapper.class, mapper -> mapper.selectById(1003L));
        assertThat(crossTenant).isNull();
        TenantContext.setTenantId("tenant-a");
        JobPO ownJob = execute(JobMapper.class, mapper -> mapper.selectById(1003L));
        assertThat(ownJob.getJobName()).isEqualTo("a-new-job");
    }

    @Test
    @DisplayName("跨租户更新应返回0且不影响目标记录")
    void shouldRejectCrossTenantUpdate() {
        TenantContext.setTenantId("tenant-a");
        JobPO job = new JobPO();
        job.setId(1002L);
        job.setJobName("hacked");
        job.setVersion(0);

        int updated = execute(JobMapper.class, mapper -> mapper.updateById(job));

        assertThat(updated).isZero();
        TenantContext.setTenantId("tenant-b");
        JobPO targetJob = execute(JobMapper.class, mapper -> mapper.selectById(1002L));
        assertThat(targetJob.getJobName()).isEqualTo("b-job");
    }

    @Test
    @DisplayName("跨租户删除应返回0且不删除目标记录")
    void shouldRejectCrossTenantDelete() {
        TenantContext.setTenantId("tenant-a");

        int deleted = execute(JobMapper.class, mapper -> mapper.deleteById(1002L));

        assertThat(deleted).isZero();
        TenantContext.setTenantId("tenant-b");
        JobPO survivingJob = execute(JobMapper.class, mapper -> mapper.selectById(1002L));
        assertThat(survivingJob).isNotNull();
    }

    @Test
    @DisplayName("当前租户只能查询自己的任务日志")
    void shouldSelectOnlyCurrentTenantJobLogs() {
        TenantContext.setTenantId("tenant-b");

        List<JobLogPO> logs = execute(JobLogMapper.class, mapper -> mapper.selectList(null));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getJobName()).isEqualTo("b-job");
    }

    @Test
    @DisplayName("findLatestByJobId应只返回当前租户的日志")
    void shouldOnlyReturnCurrentTenantLogsByJobId() {
        TenantContext.setTenantId("tenant-a");

        List<JobLogPO> logs = execute(
                JobLogMapper.class,
                mapper -> mapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobLogPO>()
                                .eq(JobLogPO::getJobId, 1001L)
                                .orderByDesc(JobLogPO::getStartTime)
                                .last("LIMIT 10")));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getJobName()).isEqualTo("a-job");
    }

    @Test
    @DisplayName("缺少租户上下文应拒绝执行任务SQL")
    void shouldRejectJobSqlWithoutContext() {
        assertThatThrownBy(() -> execute(JobMapper.class, mapper -> mapper.selectList(null)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("租户上下文未装配");
    }

    private static <M, R> R execute(Class<M> mapperClass, MapperOperation<M, R> operation) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return operation.execute(session.getMapper(mapperClass));
        }
    }

    @FunctionalInterface
    interface MapperOperation<M, R> {
        R execute(M mapper);
    }
}
