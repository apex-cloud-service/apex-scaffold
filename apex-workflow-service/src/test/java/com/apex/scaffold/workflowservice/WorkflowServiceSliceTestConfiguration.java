package com.apex.scaffold.workflowservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * WebFlux 切片测试所需的 {@link SqlSessionFactory} 配置。
 *
 * <p>在 {@code @WebFluxTest} 切片上下文中，{@link WorkflowServiceApplication} 上的
 * {@code @MapperScan} 会把 {@code @Mapper} 接口注册为 MapperFactoryBean，而这些
 * Bean 需要一个 {@link SqlSessionFactory}/{@link SqlSessionTemplate} 才能完成
 * {@code checkDaoConfig()} 校验。切片上下文不包含数据源，故此处提供一个基于
 * H2 的空事务环境的真实工厂，仅用于通过 MapperFactoryBean 的初始化校验
 * （业务外设依赖均由 {@code @MockBean} 桩替，不会真正执行 SQL）。
 */
@TestConfiguration(proxyBeanMethods = false)
public class WorkflowServiceSliceTestConfiguration {

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
}
