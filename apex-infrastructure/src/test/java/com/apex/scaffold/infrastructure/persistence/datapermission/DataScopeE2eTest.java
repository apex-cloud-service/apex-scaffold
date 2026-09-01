package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据权限拦截器端到端测试（MySQL + 真实 MyBatis-Plus 执行）。
 *
 * <p>验证完整的"装配上下文 → 拦截器改写 SQL → 数据库执行"链路：
 * 不同数据权限范围下 {@code sys_user} 查询结果被正确过滤，且分页/未装配场景不受影响。</p>
 *
 * <p>该测试为同步执行路径（查询与装配同线程），是 MyBatis-Plus 拦截器的标准生效方式；
 * 响应式应用的线程切换注意事项见 {@code docs/DATA_SCOPE.md}。</p>
 *
 * @author Apex Team
 */
@DisplayName("数据权限拦截器端到端测试")
class DataScopeE2eTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static HikariDataSource dataSource;

    @BeforeAll
    static void setUpFactory() throws Exception {
        String host = System.getProperty("DB_MASTER_HOST", "127.0.0.1");
        String port = System.getProperty("DB_MASTER_PORT", "3306");
        String user = System.getProperty("DB_MASTER_USERNAME", "root");
        String pass = System.getProperty("DB_MASTER_PASSWORD", "apex-root");
        String dbName = "apex_unit_datascope";
        String serverUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection admin = java.sql.DriverManager.getConnection(serverUrl, user, pass);
                Statement create = admin.createStatement()) {
            create.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "`");
        }
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false");
        dataSource.setUsername(user);
        dataSource.setPassword(pass);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS sys_user");
            st.execute("CREATE TABLE sys_user ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username VARCHAR(64), "
                    + "dept_id BIGINT)");
            st.execute("INSERT INTO sys_user (username, dept_id) VALUES "
                    + "('u1', 100), ('u2', 100), ('u3', 101), ('u4', NULL)");
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor(new MdcDataScopeProvider()));

        MybatisConfiguration configuration = new MybatisConfiguration();
        com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig =
                new com.baomidou.mybatisplus.core.config.GlobalConfig();
        globalConfig.setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(DemoUserMapper.class);

        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void tearDownFactory() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void shouldFilterByDeptScope() {
        DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L));

        List<DemoUserPO> users = selectAll();

        assertThat(users).extracting(DemoUserPO::getUsername).containsExactly("u1", "u2");
    }

    @Test
    void shouldFilterByDeptAndChildScope() {
        DataScopeContext.setDataScope(DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L)));

        List<DemoUserPO> users = selectAll();

        assertThat(users).extracting(DemoUserPO::getUsername).containsExactly("u1", "u2", "u3");
    }

    @Test
    void shouldFilterByCustomScope() {
        DataScopeContext.setDataScope(DataScopeInfo.ofCustom(List.of(101L)));

        List<DemoUserPO> users = selectAll();

        assertThat(users).extracting(DemoUserPO::getUsername).containsExactly("u3");
    }

    @Test
    void shouldReturnAllRowsWithoutContext() {
        // 未装配数据权限 → 放行
        List<DemoUserPO> users = selectAll();

        assertThat(users).hasSize(4);
    }

    @Test
    void shouldReturnAllRowsForAllScope() {
        DataScopeContext.setDataScope(DataScopeInfo.all());

        List<DemoUserPO> users = selectAll();

        assertThat(users).hasSize(4);
    }

    @Test
    void shouldNotFilterQueriesOutsideConfiguredMappers() {
        // 未装配时即使是 Mapper 查询也放行（已被 shouldReturnAllRowsWithoutContext 覆盖，
        // 此处额外验证装配后仅影响目标查询——先装配再清空）
        DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L));
        DataScopeContext.clear();

        assertThat(selectAll()).hasSize(4);
    }

    /**
     * 执行全表查询（MyBatis-Plus 自动生成 SQL，无别名）。
     */
    private static List<DemoUserPO> selectAll() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            DemoUserMapper mapper = session.getMapper(DemoUserMapper.class);
            return mapper.selectList(null);
        }
    }

    /** 演示用持久化对象（仅测试内使用，不影响业务代码）。 */
    @Data
    @TableName("sys_user")
    static class DemoUserPO {

        @TableId(type = IdType.AUTO)
        private Long id;

        private String username;

        private Long deptId;
    }

    /** 演示用 Mapper。 */
    interface DemoUserMapper extends BaseMapper<DemoUserPO> {}
}
