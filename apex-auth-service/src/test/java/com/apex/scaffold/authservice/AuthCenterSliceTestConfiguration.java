package com.apex.scaffold.authservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

/**
 * WebFlux 切片测试所需的数据访问基础设施配置。
 *
 * <p>在 {@code @WebFluxTest} 切片上下文中，{@link AuthServiceApplication} 上的
 * {@code @MapperScan} 会把 {@code @Mapper} 接口注册为 MapperFactoryBean，而这些
 * Bean 需要一个 {@link SqlSessionFactory}/{@link SqlSessionTemplate} 才能完成
 * {@code checkDaoConfig()} 校验。切片上下文不包含数据源，故此处提供一个基于
 * H2 的空事务环境的真实工厂，仅用于通过 MapperFactoryBean 的初始化校验
 * （业务外设依赖均由 {@code @MockBean} 桩替，不会真正执行 SQL）。
 *
 * <p>同理，{@code @EnableR2dbcRepositories} 会注册 {@code ReactiveCrudRepository}
 * 的 Bean，它们依赖名为 {@code r2dbcEntityTemplate} 的 {@link R2dbcEntityOperations}
 * Bean。切片上下文不包含该 Bean，故此处补充一个基于 H2 内存库的
 * {@link R2dbcEntityTemplate}，仅供仓储工厂完成装配校验。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthCenterSliceTestConfiguration {

    @Bean
    SqlSessionFactory sqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), new JdbcDataSource()));
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @Bean
    ConnectionFactory connectionFactory() {
        return ConnectionFactories.get("r2dbc:h2:mem:///auth-slice;DB_CLOSE_DELAY=-1");
    }

    @Bean
    R2dbcEntityOperations r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
